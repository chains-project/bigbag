package com.example.core.prompt;

import chains.changeimpact.model.ApiChangeMatch;
import chains.changeimpact.model.ChangeImpactReport;
import chains.changeimpact.model.ConstructImpact;
import com.example.core.config.EnvConfig;
import com.example.core.model.BreakingUpdateRecord;
import com.example.core.model.ClassificationSummary;
import com.example.core.model.UpdatedDependency;
import com.example.core.service.ChangeImpactReportService.FileImpact;
import com.example.core.service.ChangeImpactReportService.ErrorImpact;
import com.example.japicmp.JapicmpDiffTool;
import com.example.japicmp.model.ClassChange;
import com.example.japicmp.model.ComparisonReport;
import com.example.japicmp.model.MemberChange;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Generates LLM-ready prompt files for each breaking update commit.
 * <p>
 * The prompt content is generated dynamically by Java classes (one per
 * prompt style), similar to Bacardi's {@code se.kth.prompt} package. No
 * external template files are required: all layout and wording lives in code.
 *
 * <p>Available placeholder-style values passed to formatters:</p>
 * <ul>
 *   <li>{{PROJECT}}</li>
 *   <li>{{BREAKING_COMMIT}}</li>
 *   <li>{{DATASET_CATEGORY}}</li>
 *   <li>{{INFERRED_CATEGORY}}</li>
 *   <li>{{DEPENDENCY_GROUP_ID}}</li>
 *   <li>{{DEPENDENCY_ARTIFACT_ID}}</li>
 *   <li>{{DEPENDENCY_PREVIOUS_VERSION}}</li>
 *   <li>{{DEPENDENCY_NEW_VERSION}}</li>
 *   <li>{{OUTPUT_BASE_DIR}}</li>
 *   <li>{{COMMIT_OUTPUT_DIR}}</li>
 *   <li>{{BREAKING_CLASSIFIER_REPORT}}</li>
 *   <li>{{CHANGE_IMPACT_REPORT}}</li>
 *   <li>{{BREAKING_CHANGES_REPORT}}</li>
 *   <li>{{FILE_RELATED_CHANGES}}</li>
 *   <li>{{FILE_PATH}}</li>
 *   <li>{{FILE_ERROR_COUNT}}</li>
 *   <li>{{FILE_ERRORS}}</li>
 * </ul>
 */
public class PromptGenerationService {

    private static final Logger log = LoggerFactory.getLogger(PromptGenerationService.class);

    private final EnvConfig envConfig;
    private final ObjectMapper mapper;

    public PromptGenerationService(EnvConfig envConfig) {
        this.envConfig = envConfig;
        this.mapper = new ObjectMapper();
    }

    /**
     * Generate a prompt for a given breaking update and write it under the
     * per-commit reports directory.
     *
     * @param record          dataset record
     * @param summary         classification summary
     * @param commitReportDir reports/{commit} directory (used for JSON paths in placeholders)
     * @param outputBaseDir   output base directory; the prompt is written under output/{commit}
     */
    public void generatePrompt(BreakingUpdateRecord record,
                               ClassificationSummary summary,
                               Path commitReportDir,
                               Path outputBaseDir) {
        // Currently unused: all prompts are generated per-file via formatters.
        // Kept as a placeholder for potential future commit-level prompts.
    }

    /**
     * Generate one prompt per file that has errors.
     * <p>
     * Prompts are written under: {@code output/{commit}/prompts/{class}/{sanitizedFilePath}.txt}
     * where {@code {class}} comes from the PROMPT_CLASSES configuration.
     */
    public void generateFilePrompts(BreakingUpdateRecord record,
                                    ClassificationSummary summary,
                                    Path commitReportDir,
                                    Path outputBaseDir,
                                    java.util.List<FileImpact> files) {
        if (record == null || commitReportDir == null || outputBaseDir == null || record.breakingCommit() == null) {
            return;
        }
        if (files == null || files.isEmpty()) {
            return;
        }

        // Determine logical prompt kinds (e.g., default, in_context, anthropic_spoon_rules)
        String classesRaw = envConfig.get("PROMPT_CLASSES").orElse("default");
        String[] classTokens = classesRaw.split("[,;]");
        java.util.List<PromptKind> kinds = new java.util.ArrayList<>();
        for (String token : classTokens) {
            PromptKind.fromConfigToken(token).ifPresent(kinds::add);
        }
        if (kinds.isEmpty()) {
            kinds.add(PromptKind.DEFAULT);
        }

        // Store prompts next to the per-commit JSON reports, under:
        // {parentOfJSON_OUTPUT}/{commit}/prompts/
        Path basePromptsDir = commitReportDir.resolve("prompts");
        try {
            Files.createDirectories(basePromptsDir);
        } catch (IOException e) {
            log.warn("Failed to create prompts directory {}: {}", basePromptsDir, e.getMessage());
            return;
        }

        for (FileImpact fileImpact : files) {
            if (fileImpact == null || fileImpact.errors() == null || fileImpact.errors().isEmpty()) {
                continue;
            }

            String sanitizedFileName = sanitizeFilePath(fileImpact.filePath());
            if (sanitizedFileName.isBlank()) {
                continue;
            }

            Map<String, String> baseValues = buildGlobalPlaceholderValues(record, summary, commitReportDir, outputBaseDir);
            Map<String, String> fileValues = buildFilePlaceholderValues(baseValues, fileImpact);

            for (PromptKind kind : kinds) {
                FilePromptFormatter formatter = resolveFormatterForKind(kind);
                if (formatter == null) {
                    continue;
                }

                String rendered = formatter.build(record, summary, fileValues, fileImpact);

                // Do not create a directory per class; encode kind id into the filename:
                // {sanitizedFileName}_{kindId}_prompt.txt
                String fileName = sanitizedFileName + "_" + kind.id() + "_prompt.txt";
                Path target = basePromptsDir.resolve(fileName);
                try {
                    Files.writeString(target, rendered, StandardCharsets.UTF_8);
                    log.info("Wrote file-level prompt for {} ({}) to {}", record.breakingCommit(), fileImpact.filePath(), target);
                } catch (IOException e) {
                    log.warn("Failed to write file-level prompt for {} ({}): {}", record.breakingCommit(), fileImpact.filePath(), e.getMessage());
                }
            }
        }
    }

    private Map<String, String> buildGlobalPlaceholderValues(BreakingUpdateRecord record,
                                                             ClassificationSummary summary,
                                                             Path commitReportDir,
                                                             Path outputBaseDir) {
        Map<String, String> values = new HashMap<>();

        values.put("PROJECT", record.project());
        values.put("BREAKING_COMMIT", record.breakingCommit());

        if (summary != null) {
            values.put("DATASET_CATEGORY", summary.datasetCategory());
            values.put("INFERRED_CATEGORY", summary.inferredCategory());
        }

        UpdatedDependency dep = record.updatedDependency();
        if (dep != null) {
            values.put("DEPENDENCY_GROUP_ID", dep.dependencyGroupId());
            values.put("DEPENDENCY_ARTIFACT_ID", dep.dependencyArtifactId());
            values.put("DEPENDENCY_PREVIOUS_VERSION", dep.previousVersion());
            values.put("DEPENDENCY_NEW_VERSION", dep.newVersion());
        }

        if (outputBaseDir != null && record.breakingCommit() != null) {
            Path commitOutputDir = outputBaseDir.resolve(record.breakingCommit());
            values.put("OUTPUT_BASE_DIR", outputBaseDir.toAbsolutePath().toString());
            values.put("COMMIT_OUTPUT_DIR", commitOutputDir.toAbsolutePath().toString());
        }

        // Paths to JSON reports inside reports/{commit} – passed as references
        Path classifier = commitReportDir.resolve("breaking-classifier-report.json");
        Path changeImpact = commitReportDir.resolve("change-impact.json");
        Path breakingChanges = commitReportDir.resolve("breaking-changes.json");

        values.put("BREAKING_CLASSIFIER_REPORT", classifier.toAbsolutePath().toString());
        values.put("CHANGE_IMPACT_REPORT", changeImpact.toAbsolutePath().toString());
        values.put("BREAKING_CHANGES_REPORT", breakingChanges.toAbsolutePath().toString());

        return values;
    }

    private Map<String, String> buildFilePlaceholderValues(Map<String, String> baseValues,
                                                           FileImpact fileImpact) {
        Map<String, String> values = new HashMap<>(baseValues);
        values.put("FILE_PATH", fileImpact.filePath());
        int errorCount = fileImpact.errors() != null ? fileImpact.errors().size() : 0;
        values.put("FILE_ERROR_COUNT", Integer.toString(errorCount));
        values.put("FILE_ERRORS", buildErrorsBlock(fileImpact.errors()));
        values.put("FILE_RELATED_CHANGES", buildRelatedChangesBlock(fileImpact.errors()));
        // For file-scoped prompts (including Anthropic Spoon rules), use only
        // the API changes related to constructs on the failing lines.
        values.put("DEPENDENCY_CHANGE_DIFF", buildFileApiDiff(fileImpact.errors()));
        return values;
    }

    private String buildErrorsBlock(java.util.List<ErrorImpact> errors) {
        if (errors == null || errors.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (ErrorImpact e : errors) {
            if (e == null) {
                continue;
            }
            sb.append("Line ")
                    .append(e.lineNumber());
            if (e.columnNumber() != null) {
                sb.append(":").append(e.columnNumber());
            }
            sb.append(" - ").append(e.message() != null ? e.message() : "");
            if (e.details() != null && !e.details().isEmpty()) {
                sb.append(" [details: ").append(String.join(" | ", e.details())).append("]");
            }
            sb.append(System.lineSeparator());
        }
        return sb.toString().trim();
    }

    private String sanitizeFilePath(String filePath) {
        if (filePath == null || filePath.isBlank()) {
            return "";
        }
        String normalized = filePath.replace("\\", "/");
        // Replace directory separators and other unsafe characters
        normalized = normalized.replace("/", "__")
                .replace(":", "_")
                .replace(" ", "_");
        return normalized;
    }

    /**
     * Build a textual summary of breaking changes related to the constructs
     * identified at the failing line(s) of this file.
     *
     * This uses the in-memory {@link ChangeImpactReport} attached to each
     * {@link ErrorImpact}, instead of scanning the entire breaking-changes
     * report. Only API changes actually mapped to constructs on the error line
     * are included.
     */
    private String buildRelatedChangesBlock(java.util.List<ErrorImpact> errors) {
        if (errors == null || errors.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        boolean any = false;

        for (ErrorImpact error : errors) {
            if (error == null || error.changeImpact() == null) {
                continue;
            }
            ChangeImpactReport changeImpact = error.changeImpact();
            if (changeImpact.constructs() == null || changeImpact.constructs().isEmpty()) {
                continue;
            }

            sb.append("Error at line ")
              .append(error.lineNumber())
              .append(": ")
              .append(error.message() != null ? error.message() : "")
              .append(System.lineSeparator());

            for (ConstructImpact construct : changeImpact.constructs()) {
                if (construct == null || construct.apiChanges() == null || construct.apiChanges().isEmpty()) {
                    continue;
                }

                sb.append("  Construct: ")
                  .append(construct.constructType())
                  .append(' ')
                  .append(construct.signature() != null ? construct.signature() : "")
                  .append(System.lineSeparator());

                for (ApiChangeMatch match : construct.apiChanges()) {
                    if (match == null) {
                        continue;
                    }
                    any = true;

                    sb.append("    ")
                      .append(ApiChangeTextFormatter.format(match))
                      .append(System.lineSeparator());
                }
            }

            sb.append(System.lineSeparator());
        }

        return any ? sb.toString().trim() : "";
    }

    /**
     * Build a concise API diff for this file only: one line per related
     * {@link ApiChangeMatch}, without extra error/construct headers.
     * <p>
     * This is used to fill {{DEPENDENCY_CHANGE_DIFF}} in per-file prompts
     * (e.g. Anthropic Spoon rules), restricted strictly to the changes
     * associated with constructs on the failing lines.
     */
    private String buildFileApiDiff(java.util.List<ErrorImpact> errors) {
        if (errors == null || errors.isEmpty()) {
            return "";
        }

        java.util.LinkedHashSet<String> lines = new java.util.LinkedHashSet<>();

        for (ErrorImpact error : errors) {
            if (error == null || error.changeImpact() == null) {
                continue;
            }
            ChangeImpactReport changeImpact = error.changeImpact();
            if (changeImpact.constructs() == null || changeImpact.constructs().isEmpty()) {
                continue;
            }

            for (ConstructImpact construct : changeImpact.constructs()) {
                if (construct == null || construct.apiChanges() == null || construct.apiChanges().isEmpty()) {
                    continue;
                }
                for (ApiChangeMatch match : construct.apiChanges()) {
                    if (match == null) {
                        continue;
                    }
                    String formatted = ApiChangeTextFormatter.format(match);
                    if (!formatted.isBlank()) {
                        lines.add(formatted);
                    }
                }
            }
        }

        if (lines.isEmpty()) {
            return "";
        }
        return String.join(System.lineSeparator(), lines);
    }

    /**
     * Build a textual summary of ALL breaking API changes of the dependency,
     * line by line, to be used in prompts that need a global diff
     * (e.g. Anthropic Spoon-rule prompt).
     *
     * Uses the same japicmp comparison process as the breaking-changes export,
     * but does not read any JSON files from disk.
     */
    private String buildDependencyChangeDiff(BreakingUpdateRecord record, Path outputBaseDir) {
        if (record == null || outputBaseDir == null || record.breakingCommit() == null) {
            return "";
        }
        UpdatedDependency dep = record.updatedDependency();
        if (dep == null) {
            return "";
        }

        Path commitOutputDir = outputBaseDir.resolve(record.breakingCommit());
        if (!Files.isDirectory(commitOutputDir)) {
            return "";
        }

        Path oldJar = resolveJar(commitOutputDir, dep.dependencyArtifactId(), dep.previousVersion());
        Path newJar = resolveJar(commitOutputDir, dep.dependencyArtifactId(), dep.newVersion());
        if (oldJar == null || newJar == null) {
            return "";
        }

        try {
            ComparisonReport comparisonReport = JapicmpDiffTool.generateComparisonReport(oldJar, newJar);
            StringBuilder sb = new StringBuilder();

            if (comparisonReport.changes() != null) {
                for (ClassChange classChange : comparisonReport.changes()) {
                    // Class-level breaking change
                    if (isBreakingChange(classChange.changeStatus(),
                            classChange.binaryCompatible(),
                            classChange.sourceCompatible())) {
                        appendBreakingClassLine(sb, classChange);
                    }

                    if (classChange.detail() != null) {
                        var detail = classChange.detail();

                        if (detail.constructors() != null) {
                            for (MemberChange member : detail.constructors()) {
                                if (isBreakingChange(member.changeStatus(),
                                        member.binaryCompatible(),
                                        member.sourceCompatible())) {
                                    appendBreakingMemberLine(sb, classChange.fullyQualifiedName(), "CONSTRUCTOR", member);
                                }
                            }
                        }

                        if (detail.methods() != null) {
                            for (MemberChange member : detail.methods()) {
                                if (isBreakingChange(member.changeStatus(),
                                        member.binaryCompatible(),
                                        member.sourceCompatible())) {
                                    appendBreakingMemberLine(sb, classChange.fullyQualifiedName(), "METHOD", member);
                                }
                            }
                        }

                        if (detail.fields() != null) {
                            for (MemberChange member : detail.fields()) {
                                if (isBreakingChange(member.changeStatus(),
                                        member.binaryCompatible(),
                                        member.sourceCompatible())) {
                                    appendBreakingMemberLine(sb, classChange.fullyQualifiedName(), "FIELD", member);
                                }
                            }
                        }
                    }
                }
            }

            return sb.toString().trim();
        } catch (Exception e) {
            log.warn("Failed to build DEPENDENCY_CHANGE_DIFF from japicmp for {}: {}", record.breakingCommit(), e.getMessage());
            return "";
        }
    }

    private Path resolveJar(Path commitDir, String artifactId, String version) {
        if (commitDir == null || artifactId == null || version == null) {
            return null;
        }
        try {
            Path jar = commitDir.resolve("%s-%s.jar".formatted(artifactId, version));
            return Files.isRegularFile(jar) ? jar : null;
        } catch (Exception e) {
            log.debug("Failed to resolve jar for {}:{} in {}: {}", artifactId, version, commitDir, e.getMessage());
            return null;
        }
    }

    private boolean isBreakingChange(String changeStatus, boolean binaryCompatible, boolean sourceCompatible) {
        return "REMOVED".equalsIgnoreCase(changeStatus)
                || !sourceCompatible
                || !binaryCompatible;
    }

    private void appendBreakingClassLine(StringBuilder sb, ClassChange classChange) {
        if (classChange == null) {
            return;
        }
        if (sb.length() > 0 && sb.charAt(sb.length() - 1) != '\n') {
            sb.append(System.lineSeparator());
        }
        sb.append("- CLASS ")
          .append(classChange.fullyQualifiedName())
          .append(" [status=")
          .append(classChange.changeStatus())
          .append(", binaryCompatible=")
          .append(classChange.binaryCompatible())
          .append(", sourceCompatible=")
          .append(classChange.sourceCompatible())
          .append("]");
    }

    private void appendBreakingMemberLine(StringBuilder sb,
                                          String declaringType,
                                          String memberType,
                                          MemberChange member) {
        if (member == null || declaringType == null) {
            return;
        }
        if (sb.length() > 0 && sb.charAt(sb.length() - 1) != '\n') {
            sb.append(System.lineSeparator());
        }
        String qualifiedSignature = buildQualifiedSignature(declaringType, memberType, member);

        sb.append("- ")
          .append(memberType)
          .append(' ')
          .append(qualifiedSignature)
          .append(" [status=")
          .append(member.changeStatus())
          .append(", binaryCompatible=")
          .append(member.binaryCompatible())
          .append(", sourceCompatible=")
          .append(member.sourceCompatible())
          .append("]");

        if (member.compatibilityChanges() != null && !member.compatibilityChanges().isEmpty()) {
            String changes = member.compatibilityChanges().stream()
                    .map(cc -> cc.type() + "(" + cc.semanticVersionImpact() + ")")
                    .collect(java.util.stream.Collectors.joining(", "));
            if (!changes.isBlank()) {
                sb.append(" changes=").append(changes);
            }
        }
    }

    private String buildQualifiedSignature(String declaringType,
                                           String memberType,
                                           MemberChange member) {
        StringBuilder sb = new StringBuilder(declaringType);
        if ("METHOD".equals(memberType) || "CONSTRUCTOR".equals(memberType)) {
            sb.append("#").append(member.name()).append("(");
            if (member.parameterTypes() != null && !member.parameterTypes().isEmpty()) {
                sb.append(String.join(", ", member.parameterTypes()));
            }
            sb.append(")");
        } else if ("FIELD".equals(memberType)) {
            sb.append("::").append(member.name());
        }
        return sb.toString();
    }

    /**
     * Resolve the {@link FilePromptFormatter} implementation to use for a given
     * logical prompt class.
     * <p>
     * Environment lookup order for class {@code X} (case-insensitive):
     * <ol>
     *   <li>PROMPT_IMPL_X – fully-qualified class name implementing FilePromptFormatter</li>
     *   <li>Built-in mappings:
     *       <ul>
     *           <li>"default" → {@link DefaultFilePromptFormatter}</li>
     *           <li>"in_context" / "in-context" / "context" → {@link InContextFilePromptFormatter}</li>
     *       </ul>
     *   </li>
     * </ol>
     */
    private FilePromptFormatter resolveFormatterForClass(String promptClass) {
        if (promptClass == null || promptClass.isBlank()) {
            return new DefaultFilePromptFormatter();
        }
        String keySuffix = promptClass.toUpperCase().replace('-', '_');

        // 1) Explicit implementation override via FQCN
        String implKey = "PROMPT_IMPL_" + keySuffix;
        Optional<String> implName = envConfig.get(implKey).filter(s -> !s.isBlank());
        if (implName.isPresent()) {
            try {
                Class<?> clazz = Class.forName(implName.get());
                if (FilePromptFormatter.class.isAssignableFrom(clazz)) {
                    return (FilePromptFormatter) clazz.getDeclaredConstructor().newInstance();
                } else {
                    log.warn("Class {} does not implement FilePromptFormatter; falling back to built-ins", implName.get());
                }
            } catch (Exception e) {
                log.warn("Failed to instantiate custom FilePromptFormatter {}: {}", implName.get(), e.getMessage());
            }
        }

        // 2) Built-in mappings
        String normalized = promptClass.toLowerCase();
        return switch (normalized) {
            case "in_context", "in-context", "context" -> new InContextFilePromptFormatter();
            case "anthropic_spoon_rules" -> new AnthropicSpoonRulesFilePromptFormatter();
            case "default" -> new DefaultFilePromptFormatter();
            default -> new DefaultFilePromptFormatter();
        };
    }

    /**
     * Resolve the {@link FilePromptFormatter} implementation to use for a given
     * {@link PromptKind}.
     * <p>
     * Environment lookup order for kind {@code K}:
     * <ol>
     *   <li>PROMPT_IMPL_{K.name()} – fully-qualified class name implementing FilePromptFormatter</li>
     *   <li>LEGACY: PROMPT_IMPL_{kind.id()} – for backwards compatibility</li>
     *   <li>Built-in mappings:
     *       <ul>
     *           <li>DEFAULT → {@link DefaultFilePromptFormatter}</li>
     *           <li>IN_CONTEXT → {@link InContextFilePromptFormatter}</li>
     *           <li>ANTHROPIC_SPOON_RULES → {@link AnthropicSpoonRulesFilePromptFormatter}</li>
     *       </ul>
     *   </li>
     * </ol>
     */
    private FilePromptFormatter resolveFormatterForKind(PromptKind kind) {
        if (kind == null) {
            return new DefaultFilePromptFormatter();
        }

        // 1) Explicit implementation override via FQCN using enum name
        String primaryKey = "PROMPT_IMPL_" + kind.name();
        Optional<String> implName = envConfig.get(primaryKey).filter(s -> !s.isBlank());

        // 2) Legacy override using kind id (e.g. "anthropic")
        if (implName.isEmpty()) {
            String legacyKey = "PROMPT_IMPL_" + kind.id().toUpperCase().replace('-', '_');
            implName = envConfig.get(legacyKey).filter(s -> !s.isBlank());
        }

        if (implName.isPresent()) {
            try {
                Class<?> clazz = Class.forName(implName.get());
                if (FilePromptFormatter.class.isAssignableFrom(clazz)) {
                    return (FilePromptFormatter) clazz.getDeclaredConstructor().newInstance();
                } else {
                    log.warn("Class {} does not implement FilePromptFormatter; falling back to built-ins", implName.get());
                }
            } catch (Exception e) {
                log.warn("Failed to instantiate custom FilePromptFormatter {}: {}", implName.get(), e.getMessage());
            }
        }

        // 3) Built-in mappings
        return switch (kind) {
            case IN_CONTEXT -> new InContextFilePromptFormatter();
            case ANTHROPIC_SPOON_RULES -> new AnthropicSpoonRulesFilePromptFormatter();
            case FINAL_SPOON_RULES -> new FinalSpoonRulesFilePromptFormatter();
            case DEFAULT -> new DefaultFilePromptFormatter();
        };
    }
}


