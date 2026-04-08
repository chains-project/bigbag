package github.chains.core.prompt;

import chains.changeimpact.model.ApiChangeMatch;
import chains.changeimpact.model.ChangeImpactReport;
import chains.changeimpact.model.ConstructImpact;
import github.chains.core.config.EnvConfig;
import github.chains.core.model.BreakingUpdateRecord;
import github.chains.core.model.ClassificationSummary;
import github.chains.core.model.UpdatedDependency;
import github.chains.core.parser.Materializer;
import github.chains.core.parser.MaterializerFactory;
import github.chains.core.service.ChangeImpactReportService.FileImpact;
import github.chains.core.service.ChangeImpactReportService.ErrorImpact;
import github.chains.japicmp.JapicmpDiffTool;
import github.chains.japicmp.model.ClassChange;
import github.chains.japicmp.model.ComparisonReport;
import github.chains.japicmp.model.MemberChange;
import com.fasterxml.jackson.databind.ObjectMapper;
import github.chains.breakingclassifier.BreakingReport;
import github.chains.breakingclassifier.ErrorDetail;
import github.chains.breakingclassifier.FileErrorGroup;
import github.chains.breakingclassifier.FailureCategory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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
                                    java.util.List<FileImpact> files) throws TransformationFailureException {
        if (record == null || commitReportDir == null || outputBaseDir == null || record.breakingCommit() == null) {
            return;
        }
        if (files == null || files.isEmpty()) {
            return;
        }
        
        log.info("Starting prompt generation for {} files...", files.size());

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
                String promptFileName = sanitizedFileName + "_" + kind.id() + "_prompt.txt";
                Path promptTarget = basePromptsDir.resolve(promptFileName);
                try {
                    Files.writeString(promptTarget, rendered, StandardCharsets.UTF_8);
                    log.info("Wrote file-level prompt for {} ({}) [{}] to {}",
                            record.breakingCommit(), fileImpact.filePath(), kind.id(), promptTarget);

                    // Immediately invoke the LLM pipeline for this prompt:
                    long fileStartTime = System.currentTimeMillis();
                    invokeLlmAndMaterialize(record, commitReportDir, outputBaseDir, fileImpact, kind, promptTarget, sanitizedFileName);
                    long fileDuration = System.currentTimeMillis() - fileStartTime;
                    log.info("Completed processing for {} [{}] in {}ms", fileImpact.filePath(), kind.id(), fileDuration);
                } catch (TransformationFailureException e) {
                    // Re-throw transformation failures to be handled at a higher level
                    throw e;
                } catch (IOException | InterruptedException e) {
                    log.warn("Failed to generate or process prompt for {} ({}) [{}]: {}",
                            record.breakingCommit(), fileImpact.filePath(), kind.id(), e.getMessage());
                }
            }
        }
        
        log.info("Prompt generation completed for {} files", files.size());
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
     * End-to-end pipeline for a single file-level prompt:
     * <ol>
     *   <li>Call the Python LLM client to obtain a completion.</li>
     *   <li>Extract Spoon rules and generate a driver skeleton.</li>
     *   <li>Extract a transformed Java class from the LLM output (if present)
     *       and store it under reports/{commit}/response/.</li>
     *   <li>Generate a simple textual diff between original and transformed
     *       class under reports/{commit}/diff/.</li>
     * </ol>
     */
    private void invokeLlmAndMaterialize(BreakingUpdateRecord record,
                                         Path commitReportDir,
                                         Path outputBaseDir,
                                         FileImpact fileImpact,
                                         PromptKind kind,
                                         Path promptFile,
                                         String sanitizedFileName) throws IOException, InterruptedException, TransformationFailureException {
        if (record == null || fileImpact == null || promptFile == null) {
            return;
        }

        // Check if prompt generation should be skipped (SKIP_PROMPT_GENERATION env var)
        boolean skipPromptGeneration = envConfig.getBoolean("SKIP_PROMPT_GENERATION").orElse(false);

        Path promptsDir = promptFile.getParent();
        if (promptsDir == null) {
            promptsDir = commitReportDir;
        }
        String baseName = sanitizedFileName + "_" + kind.id();
        Path llmOutput = promptsDir.resolve(baseName + "_llm.txt");
        Path llmMeta = promptsDir.resolve(baseName + "_llm.meta.json");

        // 1) Invoke LLM client (skip if SKIP_PROMPT_GENERATION is enabled)
        if (!skipPromptGeneration) {
            callLlmClient(commitReportDir, promptFile, llmOutput, llmMeta);
        } else {
            log.info("Skipping LLM prompt generation (SKIP_PROMPT_GENERATION=true). Assuming files already exist.");
            // Verify that required files exist
            if (!Files.exists(llmOutput)) {
                log.warn("LLM output file not found (expected at {}). Skipping materialization.", llmOutput);
                return;
            }
        }

        // Resolve original source once; needed for response/original and further processing
        Path originalSource = resolveOriginalSourceFile(record, outputBaseDir, fileImpact.filePath());
        if (originalSource == null || !Files.isRegularFile(originalSource)) {
            log.warn("Could not resolve original source file for {} in {}", fileImpact.filePath(), record.breakingCommit());
            return;
        }

        // 2) Create original/ and response/ directories inside reports/{commit}
        Path originalDir = commitReportDir.resolve("original");
        Files.createDirectories(originalDir);
        // 2) Create response directory inside reports/{commit}
        Path responseDir = commitReportDir.resolve("response");
        Files.createDirectories(responseDir);

        // Copy the original source file into original/ using only the simple file name
        Path originalCopyTarget = originalDir.resolve(originalSource.getFileName());
        try {
            Files.copy(originalSource, originalCopyTarget, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            log.info("Copied original source {} to {}", originalSource, originalCopyTarget);
        } catch (IOException copyEx) {
            log.warn("Failed to copy original source {} to {}: {}", originalSource, originalCopyTarget, copyEx.getMessage());
        }

        // 3) Use appropriate materializer to extract rules and generate driver
        String rawBaseName = sanitizedFileName + "_" + kind.id();
        Materializer materializer = MaterializerFactory.getMaterializer(kind.id());
        Path spoonApplyFile = materializer.materialize(llmOutput, originalSource, commitReportDir, rawBaseName);

        // Also copy LLM output and driver into the response directory for this commit
        Path responseLlmOutput = responseDir.resolve(baseName + "_llm.txt");
        Path responseLlmMeta = responseDir.resolve(baseName + "_llm.meta.json");
        try {
            Files.copy(llmOutput, responseLlmOutput, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            if (Files.exists(llmMeta)) {
                Files.copy(llmMeta, responseLlmMeta, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException copyEx) {
            log.warn("Failed to copy LLM output/meta into response dir for {} ({}) [{}]: {}",
                    record.breakingCommit(), fileImpact.filePath(), kind.id(), copyEx.getMessage());
        }

        // 4) Apply transformation and generate diff (always, regardless of SKIP_PROMPT_GENERATION)
        // SKIP_PROMPT_GENERATION only controls the LLM call, not the transformation pipeline
        Path projectRoot = findProjectRoot(commitReportDir);
        // Create transformed directory only when we're about to write to it
        Path transformedDir = commitReportDir.resolve("transformed");
        try {
            Files.createDirectories(transformedDir);
        } catch (IOException e) {
            log.warn("Failed to create transformed directory: {}", e.getMessage());
        }
        
        String originalFileName = originalSource.getFileName().toString();
        Path transformedFile = findTransformedFile(transformedDir, originalFileName);

        // If transformed file doesn't exist, try to execute spoon_apply.java
        if (transformedFile == null || !Files.exists(transformedFile)) {
            if (Files.exists(spoonApplyFile)) {
                log.info("Executing Spoon transformation from {}", spoonApplyFile);
                // executeSpoonTransformation can throw TransformationFailureException
                executeSpoonTransformation(spoonApplyFile, commitReportDir, projectRoot, originalSource, fileImpact.filePath());
                // Re-check for transformed file after execution
                transformedFile = findTransformedFile(transformedDir, originalFileName);
            } else {
                log.warn("Spoon apply file not found at {}. Skipping transformation.", spoonApplyFile);
            }
        }

        // Generate diff if transformed file exists
        if (transformedFile != null && Files.exists(transformedFile)) {
            try {
                log.info("Generating diff for {} (original) vs {} (transformed)", originalSource, transformedFile);
                SpoonRulesMaterializer.generateDiffAfterTransformation(originalSource, commitReportDir, rawBaseName);
            } catch (Exception e) {
                log.error("Failed to generate diff: {}", e.getMessage(), e);
            }
        } else {
            log.warn("Transformed file not found. Cannot generate diff. Expected at: {}", transformedDir);
        }
    }

    /**
     * Helper method to find a transformed file recursively in a directory.
     */
    private Path findTransformedFile(Path baseDir, String fileName) throws IOException {
        if (!Files.exists(baseDir)) {
            return null;
        }
        try (var paths = Files.walk(baseDir)) {
            return paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().equals(fileName))
                    .findFirst()
                    .orElse(null);
        }
    }

    /**
     * Executes the Spoon transformation by compiling and running the spoon_apply.java file.
     * Logs are saved to a file in the spoon-rules directory.
     */
    private void executeSpoonTransformation(Path spoonApplyFile, Path commitReportDir, Path projectRoot, Path originalSource, String filePathForErrorReporting) throws IOException, InterruptedException, TransformationFailureException {
        Path spoonRulesDir = spoonApplyFile.getParent();
        
        // Create log file path in spoon-rules directory
        String logFileName = spoonApplyFile.getFileName().toString().replace(".java", "_execution.log");
        Path logFile = spoonRulesDir.resolve(logFileName);
        java.io.Writer logWriter = Files.newBufferedWriter(logFile, StandardCharsets.UTF_8);

        try {
            logWriter.write("=== Spoon Transformation Execution Log ===\n");
            logWriter.write("Java file: " + spoonApplyFile + "\n");
            logWriter.write("Started at: " + java.time.Instant.now() + "\n\n");

            // Find Spoon JAR - check environment variable first
            Optional<String> spoonJarPath = envConfig.get("SPOON_JAR");
            Path spoonJar = null;
            
            if (spoonJarPath.isPresent() && !spoonJarPath.get().isBlank()) {
                Path envJar = Paths.get(spoonJarPath.get());
                if (Files.exists(envJar)) {
                    spoonJar = envJar;
                } else {
                    log.warn("SPOON_JAR environment variable points to non-existent file: {}", envJar);
                    logWriter.write("WARNING: SPOON_JAR environment variable points to non-existent file: " + envJar + "\n");
                }
            }
            
            // Fallback: try standard Maven location
            if (spoonJar == null) {
                Path defaultJar = projectRoot.resolve("spoon-line-analyzer/target/spoon-line-analyzer-1.0.0-SNAPSHOT-jar-with-dependencies.jar");
                if (Files.exists(defaultJar)) {
                    spoonJar = defaultJar;
                }
            }
            
            if (spoonJar == null || !Files.exists(spoonJar)) {
                String errorMsg = "Spoon JAR not found. Please set SPOON_JAR environment variable or build spoon-line-analyzer.";
                log.warn(errorMsg);
                log.warn("Expected location: {}/spoon-line-analyzer/target/spoon-line-analyzer-1.0.0-SNAPSHOT-jar-with-dependencies.jar", projectRoot);
                log.warn("Build with: mvn -pl spoon-line-analyzer package");
                logWriter.write("ERROR: " + errorMsg + "\n");
                logWriter.flush();
                return;
            }

            logWriter.write("Using Spoon JAR: " + spoonJar + "\n\n");

            // Extract class name from the Java file (either from filename or by reading the file)
            String className = extractClassNameFromFile(spoonApplyFile);
            logWriter.write("Class name: " + className + "\n\n");

            // Compile the Java file
            log.info("Compiling Spoon transformation: {}", spoonApplyFile);
            logWriter.write("=== COMPILATION ===\n");
            logWriter.write("Command: javac -cp " + spoonJar + " -d " + spoonRulesDir + " " + spoonApplyFile + "\n\n");
            
            ProcessBuilder compilePb = new ProcessBuilder(
                    "javac",
                    "-cp", spoonJar.toString(),
                    "-d", spoonRulesDir.toString(),
                    spoonApplyFile.toString()
            );
            compilePb.directory(projectRoot.toFile());
            compilePb.redirectErrorStream(true);
            
            Process compileProcess = compilePb.start();
            
            // Read compilation output
            StringBuilder compileOutput = new StringBuilder();
            try (var reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(compileProcess.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    compileOutput.append(line).append("\n");
                    logWriter.write(line + "\n");
                    log.debug("Compilation: {}", line);
                }
            }
            
            int compileExitCode = compileProcess.waitFor();
            logWriter.write("\nCompilation exit code: " + compileExitCode + "\n\n");
            
            if (compileExitCode != 0) {
                log.error("Failed to compile Spoon transformation. Output:\n{}", compileOutput);
                logWriter.write("=== COMPILATION FAILED ===\n");
                logWriter.flush();
                // Report compilation error to breaking-classifier-report.json
                reportSpoonError(commitReportDir, filePathForErrorReporting, compileOutput.toString(), "COMPILATION_ERROR");
                // Throw exception to signal transformation failure
                throw new TransformationFailureException("COMPILATION_ERROR", compileOutput.toString());
            }
            log.info("Compilation successful");
            logWriter.write("=== COMPILATION SUCCESSFUL ===\n\n");

            // Execute the compiled class
            log.info("Executing Spoon transformation");
            logWriter.write("=== EXECUTION ===\n");
            String classpath = spoonJar.toString() + java.io.File.pathSeparator + spoonRulesDir.toString();
            logWriter.write("Command: java -cp " + classpath + " " + className + "\n\n");
            
            ProcessBuilder runPb = new ProcessBuilder(
                    "java",
                    "-cp", classpath,
                    className
            );
            runPb.directory(projectRoot.toFile());
            runPb.redirectErrorStream(true);
            
            Process runProcess = runPb.start();
            
            // Read execution output
            StringBuilder runOutput = new StringBuilder();
            try (var reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(runProcess.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    runOutput.append(line).append("\n");
                    logWriter.write(line + "\n");
                    log.info("Spoon: {}", line);
                }
            }
            
            int runExitCode = runProcess.waitFor();
            logWriter.write("\nExecution exit code: " + runExitCode + "\n\n");
            
            if (runExitCode != 0) {
                log.warn("Spoon transformation exited with code: {}. Output:\n{}", runExitCode, runOutput);
                logWriter.write("=== EXECUTION FAILED ===\n");
                // Report execution error to breaking-classifier-report.json
                reportSpoonError(commitReportDir, filePathForErrorReporting, runOutput.toString(), "EXECUTION_ERROR");
                // Throw exception to signal transformation failure
                throw new TransformationFailureException("EXECUTION_ERROR", runOutput.toString());
            } else {
                log.info("Spoon transformation completed successfully");
                logWriter.write("=== EXECUTION SUCCESSFUL ===\n");
            }
            
            logWriter.write("\nCompleted at: " + java.time.Instant.now() + "\n");
            logWriter.flush();
        } finally {
            logWriter.close();
        }
    }
    
    /**
     * Extracts the fully qualified class name from a Java file by reading it.
     * If the class has a package declaration, returns "package.ClassName", otherwise just "ClassName".
     */
    private String extractClassNameFromFile(Path javaFile) throws IOException {
        String content = Files.readString(javaFile, StandardCharsets.UTF_8);
        
        // First, try to extract package name
        String packageName = null;
        java.util.regex.Pattern packagePattern = java.util.regex.Pattern.compile(
            "^\\s*package\\s+([a-zA-Z_][a-zA-Z0-9_.]*)\\s*;"
        );
        java.util.regex.Matcher packageMatcher = packagePattern.matcher(content);
        if (packageMatcher.find()) {
            packageName = packageMatcher.group(1);
        }
        
        // Extract class name
        String className = null;
        java.util.regex.Pattern classPattern = java.util.regex.Pattern.compile(
            "public\\s+class\\s+([A-Za-z_][A-Za-z0-9_]*)"
        );
        java.util.regex.Matcher classMatcher = classPattern.matcher(content);
        if (classMatcher.find()) {
            className = classMatcher.group(1);
        }
        
        // If both package and class found, return fully qualified name
        if (packageName != null && className != null) {
            return packageName + "." + className;
        }
        
        // If only class found, return just the class name
        if (className != null) {
            return className;
        }
        
        // Fallback: use filename without extension
        String fileName = javaFile.getFileName().toString();
        return fileName.substring(0, fileName.lastIndexOf('.'));
    }

    /**
     * Reports Spoon transformation errors (compilation or execution) to breaking-classifier-report.json.
     */
    private void reportSpoonError(Path commitReportDir, String filePath, String errorOutput, String errorType) {
        try {
            Path classifierReport = commitReportDir.resolve("breaking-classifier-report.json");
            ObjectMapper mapper = new ObjectMapper();
            
            // Read existing report or create new one
            BreakingReport report;
            if (Files.exists(classifierReport)) {
                report = mapper.readValue(classifierReport.toFile(), BreakingReport.class);
            } else {
                String failurePath = commitReportDir.resolve("../output").toString(); // Approximate
                report = new BreakingReport(failurePath, FailureCategory.COMPILATION_FAILURE, new java.util.ArrayList<>());
            }
            
            // Use the filePath directly (already in the correct format from fileImpact.filePath())
            String originalSourcePath = filePath;
            
            // Parse error output to extract line number and message
            ErrorDetail errorDetail = parseErrorOutput(errorOutput, errorType);
            
            // Add error to report
            java.util.List<FileErrorGroup> errorsByFile = new java.util.ArrayList<>(report.errorsByFile());
            
            // Find or create FileErrorGroup for this file
            FileErrorGroup fileGroup = null;
            int fileIndex = -1;
            for (int i = 0; i < errorsByFile.size(); i++) {
                if (errorsByFile.get(i).filePath().equals(originalSourcePath)) {
                    fileGroup = errorsByFile.get(i);
                    fileIndex = i;
                    break;
                }
            }
            
            if (fileGroup == null) {
                // Create new FileErrorGroup
                java.util.List<ErrorDetail> errors = new java.util.ArrayList<>();
                errors.add(errorDetail);
                fileGroup = new FileErrorGroup(originalSourcePath, errors);
                errorsByFile.add(fileGroup);
            } else {
                // Add error to existing group
                java.util.List<ErrorDetail> errors = new java.util.ArrayList<>(fileGroup.errors());
                errors.add(errorDetail);
                fileGroup = new FileErrorGroup(originalSourcePath, errors);
                errorsByFile.set(fileIndex, fileGroup);
            }
            
            // Create updated report
            BreakingReport updatedReport = new BreakingReport(
                report.originalFailurePath(),
                FailureCategory.COMPILATION_FAILURE, // Use COMPILATION_FAILURE for Spoon errors
                errorsByFile
            );
            
            // Write updated report
            mapper.writerWithDefaultPrettyPrinter().writeValue(classifierReport.toFile(), updatedReport);
            log.info("Added Spoon {} to breaking-classifier-report.json: {}", errorType, originalSourcePath);
            
        } catch (Exception e) {
            log.error("Failed to report Spoon error to breaking-classifier-report.json: {}", e.getMessage(), e);
        }
    }


    /**
     * Parses error output to extract line number and message.
     */
    private ErrorDetail parseErrorOutput(String errorOutput, String errorType) {
        // Try to extract line number from error output (e.g., "error: ... SpoonApplyRules.java:25: ...")
        int lineNumber = 1;
        String message = errorType + ": " + (errorOutput.length() > 200 ? errorOutput.substring(0, 200) + "..." : errorOutput);
        
        // Try to find line number pattern: "filename.java:lineNumber:"
        java.util.regex.Pattern linePattern = java.util.regex.Pattern.compile(".*\\.java:(\\d+):.*");
        java.util.regex.Matcher matcher = linePattern.matcher(errorOutput);
        if (matcher.find()) {
            try {
                lineNumber = Integer.parseInt(matcher.group(1));
            } catch (NumberFormatException e) {
                // Keep default
            }
        }
        
        // Extract first meaningful error message line
        String[] lines = errorOutput.split("\n");
        for (String line : lines) {
            if (line.contains("error:") || line.contains("Error:")) {
                message = line.trim();
                break;
            }
        }
        
        return new ErrorDetail(lineNumber, null, message, java.util.List.of());
    }

    private void callLlmClient(Path commitReportDir,
                               Path promptFile,
                               Path outputFile,
                               Path metaFile) throws IOException, InterruptedException {
        long startTime = System.currentTimeMillis();
        log.info("Calling LLM client for prompt: {}", promptFile.getFileName());
        
        String script = envConfig.get("LLM_CLIENT_PY").orElse("llm/llm_client.py");
        ProcessBuilder pb = new ProcessBuilder(
                "python3",
                script,
                "--prompt-file", promptFile.toString(),
                "--output-file", outputFile.toString(),
                "--meta-file", metaFile.toString()
        );
        // Execute from the project root (parent of reports/)
        Path projectRoot = findProjectRoot(commitReportDir);
        pb.directory(projectRoot.toFile());
        pb.redirectErrorStream(true);

        Process process = pb.start();
        
        // Read output in real-time to avoid blocking issues
        try (var reader = new java.io.BufferedReader(
                new java.io.InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                // Log LLM output if verbose (optional)
            }
        }
        
        int exitCode = process.waitFor();
        long duration = System.currentTimeMillis() - startTime;
        
        if (exitCode != 0) {
            log.error("LLM client exited with code {} after {}ms", exitCode, duration);
            throw new IOException("LLM client exited with code " + exitCode);
        }
        
        log.info("LLM call completed in {}ms for {}", duration, promptFile.getFileName());
    }

    private Path findProjectRoot(Path commitReportDir) {
        Path current = commitReportDir.toAbsolutePath();
        while (current != null) {
            if (Files.isRegularFile(current.resolve("pom.xml"))) {
                return current;
            }
            current = current.getParent();
        }
        // Fallback: directory of commitReportDir
        return commitReportDir.toAbsolutePath().getParent();
    }

    private Path resolveOriginalSourceFile(BreakingUpdateRecord record,
                                           Path outputBaseDir,
                                           String filePath) {
        if (record == null || outputBaseDir == null || filePath == null || filePath.isBlank()) {
            return null;
        }
        try {
            Path commitOutputDir = outputBaseDir.resolve(record.breakingCommit());
            String normalized = filePath.replace("\\", "/");
            if (normalized.startsWith("/")) {
                normalized = normalized.substring(1);
            }
            Path candidate = commitOutputDir.resolve(normalized);
            if (Files.isRegularFile(candidate)) {
                return candidate.normalize();
            }
        } catch (Exception e) {
            log.warn("Failed to resolve original source file {} for {}: {}", filePath, record.breakingCommit(), e.getMessage());
        }
        return null;
    }

    /**
     * Try to extract a full Java class from the LLM output.
     * Strategy:
     *   - Prefer the last ```java ... ``` fenced block.
     *   - Fallback to the entire text if no code fence is found.
     */
    private String extractTransformedJavaSource(String llmText) {
        if (llmText == null || llmText.isBlank()) {
            return null;
        }
        String lower = llmText.toLowerCase();
        int lastFence = lower.lastIndexOf("```java");
        if (lastFence >= 0) {
            int start = lower.indexOf('\n', lastFence);
            if (start < 0) {
                start = lastFence + "```java".length();
            } else {
                start = start + 1;
            }
            int end = lower.indexOf("```", start);
            if (end > start) {
                return llmText.substring(start, end).trim();
            }
        }
        // Fallback: return full content
        return llmText.trim();
    }

    private Path buildResponseSourcePath(Path responseDir, Path originalSource) {
        // Store the transformed class in the response directory using only the
        // simple file name (e.g., ReportBuilder.java), without the original path.
        return responseDir.resolve(originalSource.getFileName());
    }

    /**
     * Very simple textual diff: writes both original and transformed files
     * into a patch-like structure so it can be inspected later.
     *
     * This is not a full unified-diff algorithm, but it preserves enough
     * information to inspect changes per file and per prompt.
     */
    private void generateSimpleDiff(Path original, Path transformed, Path diffFile) {
        try {
            String originalText = Files.readString(original, StandardCharsets.UTF_8);
            String transformedText = Files.readString(transformed, StandardCharsets.UTF_8);
            String nl = System.lineSeparator();
            StringBuilder sb = new StringBuilder();
            sb.append("--- ").append(original.toString()).append(nl);
            sb.append("+++ ").append(transformed.toString()).append(nl);
            sb.append("@@ ORIGINAL @@").append(nl);
            sb.append(originalText).append(nl);
            sb.append("@@ TRANSFORMED @@").append(nl);
            sb.append(transformedText).append(nl);
            Files.writeString(diffFile, sb.toString(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.warn("Failed to generate diff between {} and {}: {}", original, transformed, e.getMessage());
        }
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
            case "v2_in_context", "v2-in-context" -> new V2InContextFilePromptFormatter();
            case "baseline", "base_line", "base-line" -> new BaseLineFilePromptFormatter();
            case "baseline_spoon", "baseline-spoon", "baseline_spoon_rules" -> new BaselineSpoonFilePromptFormatter();
            case "prompt_4", "prompt4", "prompt-4" -> new Prompt4FilePromptFormatter();
            case "prompt_5", "prompt5", "prompt-5" -> new Prompt5FilePromptFormatter();
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
            case V2_IN_CONTEXT -> new V2InContextFilePromptFormatter();
            case BASELINE -> new BaseLineFilePromptFormatter();
            case BASELINE_SPOON -> new BaselineSpoonFilePromptFormatter();
            case PROMPT_4 -> new Prompt4FilePromptFormatter();
            case PROMPT_5 -> new Prompt5FilePromptFormatter();
            case DEFAULT -> new DefaultFilePromptFormatter();
        };
    }
}


