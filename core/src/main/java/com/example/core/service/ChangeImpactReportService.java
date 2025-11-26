package com.example.core.service;

import chains.changeimpact.model.ChangeImpactReport;
import chains.changeimpact.service.ChangeImpactAnalyzer;
import com.example.core.config.EnvConfig;
import com.example.core.model.BreakingUpdateRecord;
import com.example.core.model.ClassificationSummary;
import com.example.core.model.UpdatedDependency;
import com.example.core.util.ProjectPaths;
import com.example.japicmp.JapicmpDiffTool;
import com.fasterxml.jackson.databind.ObjectMapper;
import github.chains.breakingclassifier.BreakingReport;
import github.chains.breakingclassifier.ErrorDetail;
import github.chains.breakingclassifier.FileErrorGroup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Generates detailed change-impact reports by combining breaking-classifier output
 * with the ChangeImpactAnalyzer (Spoon + japicmp).
 */
public class ChangeImpactReportService {

    private static final Logger log = LoggerFactory.getLogger(ChangeImpactReportService.class);

    private final boolean verbose;
    private final ChangeImpactAnalyzer analyzer;
    private final ObjectMapper mapper;
    private final List<Path> additionalClasspath;

    public ChangeImpactReportService(boolean verbose) {
        this(verbose, null);
    }

    public ChangeImpactReportService(boolean verbose, EnvConfig envConfig) {
        this.verbose = verbose;
        this.analyzer = new ChangeImpactAnalyzer();
        this.mapper = new ObjectMapper();
        
        // Load additional classpath from environment if available
        if (envConfig != null) {
            this.additionalClasspath = envConfig.getPathList("SPOON_CLASSPATH");
            if (!this.additionalClasspath.isEmpty()) {
                log.info("Loaded {} additional classpath entries from SPOON_CLASSPATH", this.additionalClasspath.size());
            }
        } else {
            this.additionalClasspath = List.of();
        }
    }

    /**
     * Copies the breaking-classifier report into the reports directory and generates
     * a detailed change-impact report grouped by file/errors.
     *
     * @param record               dataset record
     * @param summary              classification summary
     * @param outputBaseDir        directory where projects/JARs are stored (output/{commit})
     * @param commitReportDir      directory under reports/{commit}
     * @param classifierReportPath path to the classifier JSON generated in output/{commit}
     */
    public void copyAndGenerate(BreakingUpdateRecord record,
                                ClassificationSummary summary,
                                Path outputBaseDir,
                                Path commitReportDir,
                                Path classifierReportPath) {

        if (record == null || summary == null || commitReportDir == null) {
            return;
        }

        try {
            Files.createDirectories(commitReportDir);
        } catch (IOException e) {
            log.warn("Failed to create commit report directory {}: {}", commitReportDir, e.getMessage());
            return;
        }

        Path copiedClassifierReport = null;
        if (classifierReportPath != null) {
            try {
                copiedClassifierReport = commitReportDir.resolve("breaking-classifier-report.json");
                Files.copy(classifierReportPath, copiedClassifierReport, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                log.warn("Failed to copy breaking-classifier report for {}: {}", record.breakingCommit(), e.getMessage());
                copiedClassifierReport = null;
            }
        }

        if (outputBaseDir == null || copiedClassifierReport == null) {
            // Nothing else to do
            return;
        }

        Path changeImpactTarget = commitReportDir.resolve("change-impact.json");
        try {
            DetailedChangeImpactReport report = buildDetailedReport(record, summary, outputBaseDir, copiedClassifierReport);
            if (report != null) {
                mapper.writerWithDefaultPrettyPrinter().writeValue(changeImpactTarget.toFile(), report);
            }
        } catch (Exception e) {
            log.warn("Failed to generate change-impact report for {}: {}", record.breakingCommit(), e.getMessage());
            if (verbose) {
                e.printStackTrace();
            }
        }

        // Export all breaking changes to a separate JSON file
        try {
            exportBreakingChanges(record, outputBaseDir, commitReportDir);
        } catch (Exception e) {
            log.warn("Failed to export breaking changes for {}: {}", record.breakingCommit(), e.getMessage());
            if (verbose) {
                e.printStackTrace();
            }
        }
    }

    private DetailedChangeImpactReport buildDetailedReport(BreakingUpdateRecord record,
                                                           ClassificationSummary summary,
                                                           Path outputBaseDir,
                                                           Path classifierReportPath) throws IOException {

        BreakingReport breakingReport = mapper.readValue(classifierReportPath.toFile(), BreakingReport.class);

        UpdatedDependency dependency = record.updatedDependency();
        if (dependency == null) {
            log.debug("Record {} has no dependency info; skipping change-impact report", record.breakingCommit());
            return basicReport(record, summary, Map.of(), List.of());
        }

        Path commitOutputDir = outputBaseDir.resolve(record.breakingCommit());
        if (!Files.isDirectory(commitOutputDir)) {
            log.warn("Commit directory {} not found for {}", commitOutputDir, record.breakingCommit());
            return null;
        }

        Path projectDir = ProjectPaths.resolveProjectDir(commitOutputDir, record.project());
        if (projectDir == null || !Files.isDirectory(projectDir)) {
            log.warn("Project directory not found under {}", commitOutputDir);
            return null;
        }

        Optional<Path> maybeOldJar = resolveJar(commitOutputDir, dependency.dependencyArtifactId(), dependency.previousVersion());
        Optional<Path> maybeNewJar = resolveJar(commitOutputDir, dependency.dependencyArtifactId(), dependency.newVersion());

        if (maybeOldJar.isEmpty() || maybeNewJar.isEmpty()) {
            log.warn("Missing JARs for dependency {} in commit {}", dependency.dependencyArtifactId(), record.breakingCommit());
            return null;
        }

        Path oldJar = maybeOldJar.get();
        Path newJar = maybeNewJar.get();

        ChangeImpactAnalyzer.Session analyzerSession;
        try {
            analyzerSession = this.analyzer.openSession(projectDir, oldJar, newJar, additionalClasspath);
        } catch (Exception sessionError) {
            log.warn("Failed to initialize change-impact session for {}: {}", record.breakingCommit(), sessionError.getMessage());
            if (verbose) {
                sessionError.printStackTrace();
            }
            return null;
        }

        List<FileImpact> fileImpacts = new ArrayList<>();
        for (FileErrorGroup group : breakingReport.errorsByFile()) {
            Path sourceFile = resolveSourceFile(commitOutputDir, projectDir, group.filePath());
            if (sourceFile == null) {
                log.warn("Could not resolve source file {} for {}", group.filePath(), record.breakingCommit());
                continue;
            }

            List<ErrorImpact> errorImpacts = new ArrayList<>();
            for (ErrorDetail errorDetail : group.errors()) {
                if (errorDetail == null || errorDetail.lineNumber() < 1) {
                    continue;
                }

                try {
                    ChangeImpactReport changeImpact = analyzerSession.analyze(
                            relativizeOrSelf(projectDir, sourceFile),
                            errorDetail.lineNumber()
                    );
                    errorImpacts.add(new ErrorImpact(
                            errorDetail.lineNumber(),
                            errorDetail.columnNumber(),
                            errorDetail.message(),
                            errorDetail.details(),
                            "SUCCESS",
                            null,
                            changeImpact
                    ));
                } catch (Exception ex) {
                    log.warn("Change-impact failed for {}:{} -> {}", group.filePath(), errorDetail.lineNumber(), ex.getMessage());
                    if (verbose) {
                        ex.printStackTrace();
                    }
                    errorImpacts.add(new ErrorImpact(
                            errorDetail.lineNumber(),
                            errorDetail.columnNumber(),
                            errorDetail.message(),
                            errorDetail.details(),
                            "FAILED",
                            ex.getMessage(),
                            null
                    ));
                }
            }

            if (!errorImpacts.isEmpty()) {
                fileImpacts.add(new FileImpact(group.filePath(), errorImpacts));
            }
        }

        Map<String, Object> dependencyInfo = new LinkedHashMap<>();
        dependencyInfo.put("groupId", dependency.dependencyGroupId());
        dependencyInfo.put("artifactId", dependency.dependencyArtifactId());
        dependencyInfo.put("previousVersion", dependency.previousVersion());
        dependencyInfo.put("newVersion", dependency.newVersion());
        dependencyInfo.put("previousJar", maybeOldJar.map(Path::toString).orElse(null));
        dependencyInfo.put("newJar", maybeNewJar.map(Path::toString).orElse(null));

        Map<String, Object> classification = new LinkedHashMap<>();
        classification.put("datasetCategory", summary.datasetCategory());
        classification.put("inferredCategory", summary.inferredCategory());
        classification.put("logFile", summary.logFile());
        classification.put("classifierReport", classifierReportPath.toString());

        return new DetailedChangeImpactReport(
                record.project(),
                record.breakingCommit(),
                dependencyInfo,
                classification,
                fileImpacts
        );
    }

    private DetailedChangeImpactReport basicReport(BreakingUpdateRecord record,
                                                   ClassificationSummary summary,
                                                   Map<String, Object> dependency,
                                                   List<FileImpact> files) {
        Map<String, Object> classification = new LinkedHashMap<>();
        classification.put("datasetCategory", summary != null ? summary.datasetCategory() : null);
        classification.put("inferredCategory", summary != null ? summary.inferredCategory() : null);
        classification.put("logFile", summary != null ? summary.logFile() : null);
        classification.put("classifierReport", summary != null ? summary.classifierReport() : null);

        return new DetailedChangeImpactReport(
                record.project(),
                record.breakingCommit(),
                dependency,
                classification,
                files
        );
    }

    private Optional<Path> resolveJar(Path commitDir, String artifactId, String version) {
        if (artifactId == null || version == null) {
            return Optional.empty();
        }
        Path jar = commitDir.resolve("%s-%s.jar".formatted(artifactId, version));
        if (Files.isRegularFile(jar)) {
            return Optional.of(jar);
        }
        return Optional.empty();
    }

    private Path resolveSourceFile(Path commitDir, Path projectDir, String filePath) {
        if (filePath == null || filePath.isBlank()) {
            return null;
        }

        try {
            Path rawPath = Paths.get(filePath.replace("\\", "/"));
            if (rawPath.isAbsolute()) {
                rawPath = rawPath.getNameCount() > 0 ? rawPath.subpath(0, rawPath.getNameCount()) : rawPath;
            }

            Path candidate = commitDir.resolve(stripLeadingSeparator(rawPath));
            if (Files.isRegularFile(candidate)) {
                return candidate.normalize();
            }

            if (projectDir != null) {
                Path relative = stripProjectPrefix(projectDir, rawPath);
                candidate = projectDir.resolve(relative);
                if (Files.isRegularFile(candidate)) {
                    return candidate.normalize();
                }
            }

        } catch (Exception e) {
            log.warn("Failed to resolve source file {}: {}", filePath, e.getMessage());
        }
        return null;
    }

    private Path stripLeadingSeparator(Path path) {
        if (path.getNameCount() == 0) {
            return path;
        }
        if (path.toString().startsWith("/") || path.toString().startsWith("\\")) {
            return path.subpath(0, path.getNameCount());
        }
        return path;
    }

    private Path stripProjectPrefix(Path projectDir, Path rawPath) {
        if (projectDir == null || rawPath.getNameCount() == 0) {
            return rawPath;
        }
        Path projectName = projectDir.getFileName();
        if (projectName != null && rawPath.getNameCount() > 0 &&
                projectName.toString().equals(rawPath.getName(0).toString())) {
            if (rawPath.getNameCount() == 1) {
                return Paths.get(".");
            }
            return rawPath.subpath(1, rawPath.getNameCount());
        }
        return rawPath;
    }

    private Path relativizeOrSelf(Path projectDir, Path sourceFile) {
        try {
            return projectDir.relativize(sourceFile);
        } catch (Exception e) {
            return sourceFile;
        }
    }

    /**
     * Top-level payload for change-impact.json
     */
    public record DetailedChangeImpactReport(
            String project,
            String breakingCommit,
            Map<String, Object> dependency,
            Map<String, Object> classification,
            List<FileImpact> files
    ) {
    }

    public record FileImpact(
            String filePath,
            List<ErrorImpact> errors
    ) {
    }

    public record ErrorImpact(
            int lineNumber,
            Integer columnNumber,
            String message,
            List<String> details,
            String status,
            String errorMessage,
            ChangeImpactReport changeImpact
    ) {
    }

    /**
     * Exports all breaking changes from the API comparison to a JSON file.
     * Breaking changes include: removed classes/methods/fields, incompatible modifications, etc.
     */
    private void exportBreakingChanges(BreakingUpdateRecord record,
                                      Path outputBaseDir,
                                      Path commitReportDir) throws IOException {
        UpdatedDependency dependency = record.updatedDependency();
        if (dependency == null) {
            return;
        }

        Path commitOutputDir = outputBaseDir.resolve(record.breakingCommit());
        if (!Files.isDirectory(commitOutputDir)) {
            return;
        }

        Optional<Path> maybeOldJar = resolveJar(commitOutputDir, dependency.dependencyArtifactId(), dependency.previousVersion());
        Optional<Path> maybeNewJar = resolveJar(commitOutputDir, dependency.dependencyArtifactId(), dependency.newVersion());

        if (maybeOldJar.isEmpty() || maybeNewJar.isEmpty()) {
            return;
        }

        Path oldJar = maybeOldJar.get();
        Path newJar = maybeNewJar.get();

        try {
            com.example.japicmp.model.ComparisonReport comparisonReport = 
                JapicmpDiffTool.generateComparisonReport(oldJar, newJar);
            
            BreakingChangesReport breakingChangesReport = extractBreakingChanges(record, dependency, comparisonReport);
            
            Path breakingChangesTarget = commitReportDir.resolve("breaking-changes.json");
            mapper.writerWithDefaultPrettyPrinter().writeValue(breakingChangesTarget.toFile(), breakingChangesReport);
            
            log.info("Exported {} breaking changes to {}", 
                    breakingChangesReport.breakingChanges().size(), breakingChangesTarget);
        } catch (Exception e) {
            log.warn("Failed to generate breaking changes report: {}", e.getMessage());
            if (verbose) {
                e.printStackTrace();
            }
        }
    }

    private BreakingChangesReport extractBreakingChanges(BreakingUpdateRecord record,
                                                         UpdatedDependency dependency,
                                                         com.example.japicmp.model.ComparisonReport comparisonReport) {
        List<BreakingChangeEntry> breakingChanges = new ArrayList<>();

        if (comparisonReport.changes() != null) {
            for (com.example.japicmp.model.ClassChange classChange : comparisonReport.changes()) {
                // Check if class itself is breaking
                if (isBreakingChange(classChange.changeStatus(), 
                                    classChange.binaryCompatible(), 
                                    classChange.sourceCompatible())) {
                    breakingChanges.add(createClassBreakingChange(classChange));
                }

                // Check members (methods, constructors, fields)
                if (classChange.detail() != null) {
                    com.example.japicmp.model.ClassDetail detail = classChange.detail();
                    
                    if (detail.constructors() != null) {
                        for (com.example.japicmp.model.MemberChange member : detail.constructors()) {
                            if (isBreakingChange(member.changeStatus(), 
                                                member.binaryCompatible(), 
                                                member.sourceCompatible())) {
                                breakingChanges.add(createMemberBreakingChange(
                                    classChange.fullyQualifiedName(), 
                                    "CONSTRUCTOR", 
                                    member));
                            }
                        }
                    }

                    if (detail.methods() != null) {
                        for (com.example.japicmp.model.MemberChange member : detail.methods()) {
                            if (isBreakingChange(member.changeStatus(), 
                                                member.binaryCompatible(), 
                                                member.sourceCompatible())) {
                                breakingChanges.add(createMemberBreakingChange(
                                    classChange.fullyQualifiedName(), 
                                    "METHOD", 
                                    member));
                            }
                        }
                    }

                    if (detail.fields() != null) {
                        for (com.example.japicmp.model.MemberChange member : detail.fields()) {
                            if (isBreakingChange(member.changeStatus(), 
                                                member.binaryCompatible(), 
                                                member.sourceCompatible())) {
                                breakingChanges.add(createMemberBreakingChange(
                                    classChange.fullyQualifiedName(), 
                                    "FIELD", 
                                    member));
                            }
                        }
                    }
                }
            }
        }

        return new BreakingChangesReport(
                record.project(),
                record.breakingCommit(),
                dependency.dependencyGroupId(),
                dependency.dependencyArtifactId(),
                dependency.previousVersion(),
                dependency.newVersion(),
                comparisonReport.oldJar(),
                comparisonReport.newJar(),
                comparisonReport.generatedAt(),
                breakingChanges.size(),
                breakingChanges
        );
    }

    private boolean isBreakingChange(String changeStatus, boolean binaryCompatible, boolean sourceCompatible) {
        // A change is breaking if:
        // 1. It's REMOVED (always breaking)
        // 2. It's not source compatible (breaking for source code)
        // 3. It's not binary compatible (breaking for compiled code)
        return "REMOVED".equalsIgnoreCase(changeStatus) 
                || !sourceCompatible 
                || !binaryCompatible;
    }

    private BreakingChangeEntry createClassBreakingChange(com.example.japicmp.model.ClassChange classChange) {
        String qualifiedSignature = classChange.fullyQualifiedName();
        return new BreakingChangeEntry(
                "CLASS",
                classChange.fullyQualifiedName(),
                classChange.simpleName(),
                qualifiedSignature,
                classChange.changeStatus(),
                classChange.binaryCompatible(),
                classChange.sourceCompatible(),
                classChange.compatibilityChanges() != null 
                    ? classChange.compatibilityChanges().stream()
                        .map(cc -> new CompatibilityChangeSummary(
                            cc.type(),
                            cc.binaryCompatible(),
                            cc.sourceCompatible(),
                            cc.semanticVersionImpact()))
                        .collect(java.util.stream.Collectors.toList())
                    : List.of(),
                null, // signature (not applicable for classes)
                null, // value (not applicable for classes)
                classChange.detail() != null ? classChange.detail().oldModifiers() : List.of(),
                classChange.detail() != null ? classChange.detail().newModifiers() : List.of(),
                null // parameterTypes (not applicable for classes)
        );
    }

    private BreakingChangeEntry createMemberBreakingChange(String declaringType,
                                                          String memberType,
                                                          com.example.japicmp.model.MemberChange member) {
        String qualifiedSignature = buildQualifiedSignature(declaringType, memberType, member);
        return new BreakingChangeEntry(
                memberType,
                member.name(),
                member.name(),
                qualifiedSignature,
                member.changeStatus(),
                member.binaryCompatible(),
                member.sourceCompatible(),
                member.compatibilityChanges() != null
                    ? member.compatibilityChanges().stream()
                        .map(cc -> new CompatibilityChangeSummary(
                            cc.type(),
                            cc.binaryCompatible(),
                            cc.sourceCompatible(),
                            cc.semanticVersionImpact()))
                        .collect(java.util.stream.Collectors.toList())
                    : List.of(),
                member.signature() != null 
                    ? new ValueChangeSummary(
                        member.signature().oldValue(),
                        member.signature().newValue(),
                        member.signature().changed())
                    : null,
                member.value() != null
                    ? new ValueChangeSummary(
                        member.value().oldValue(),
                        member.value().newValue(),
                        member.value().changed())
                    : null,
                member.oldModifiers() != null ? member.oldModifiers() : List.of(),
                member.newModifiers() != null ? member.newModifiers() : List.of(),
                member.parameterTypes() != null ? member.parameterTypes() : List.of()
        );
    }

    private String buildQualifiedSignature(String declaringType, String memberType, com.example.japicmp.model.MemberChange member) {
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

    public record BreakingChangesReport(
            String project,
            String breakingCommit,
            String dependencyGroupId,
            String dependencyArtifactId,
            String previousVersion,
            String newVersion,
            String oldJar,
            String newJar,
            String generatedAt,
            int totalBreakingChanges,
            List<BreakingChangeEntry> breakingChanges
    ) {
    }

    public record BreakingChangeEntry(
            String elementType,
            String name,
            String simpleName,
            String qualifiedSignature,
            String changeStatus,
            boolean binaryCompatible,
            boolean sourceCompatible,
            List<CompatibilityChangeSummary> compatibilityChanges,
            ValueChangeSummary signature,
            ValueChangeSummary value,
            List<String> oldModifiers,
            List<String> newModifiers,
            List<String> parameterTypes
    ) {
    }

    public record ValueChangeSummary(
            String oldValue,
            String newValue,
            boolean changed
    ) {
    }

    public record CompatibilityChangeSummary(
            String type,
            boolean binaryCompatible,
            boolean sourceCompatible,
            String semanticVersionImpact
    ) {
    }
}

