package com.example.core.service;

import com.example.core.model.BreakingUpdateRecord;
import github.chains.breakingclassifier.BreakingClassifierApp;
import github.chains.breakingclassifier.BreakingReport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.kth.DockerBuild;
import se.kth.models.Attempt;
import se.kth.models.FailureCategory;
import se.kth.models.Result;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;

/**
 * Service for replacing transformed files in the project, executing builds,
 * and copying outputs to the report directory.
 */
public class TransformedFileBuildService {

    private static final Logger log = LoggerFactory.getLogger(TransformedFileBuildService.class);
    private final DockerBuild dockerBuild;
    private final boolean verbose;

    public TransformedFileBuildService(DockerBuild dockerBuild, boolean verbose) {
        this.dockerBuild = dockerBuild;
        this.verbose = verbose;
    }

    /**
     * Result of the transformed file build process.
     */
    public static class BuildResult {
        private final Path logFile;
        private final FailureCategory category;
        private final Attempt attempt;
        private final boolean success;

        public BuildResult(Path logFile, FailureCategory category, Attempt attempt, boolean success) {
            this.logFile = logFile;
            this.category = category;
            this.attempt = attempt;
            this.success = success;
        }

        public Path getLogFile() {
            return logFile;
        }

        public FailureCategory getCategory() {
            return category;
        }

        public Attempt getAttempt() {
            return attempt;
        }

        public boolean isSuccess() {
            return success;
        }
    }

    /**
     * Replaces transformed files in the project, executes build, copies output,
     * runs breaking-classifier, and returns the result.
     *
     * @param transformedDir the directory containing transformed files (reports/{model}/{commit}/transformed/)
     * @param projectDir the project directory where original files are located
     * @param dockerImage the Docker image to use for building
     * @param record the breaking update record
     * @param outputReportDir the report directory where output should be copied (reports/{model}/{commit}/)
     * @param attemptNumber the attempt number (for log file naming)
     * @return BuildResult with log file path, category, and attempt, or null if failed
     */
    public BuildResult replaceAndBuild(Path transformedDir, Path projectDir, String dockerImage,
                                       BreakingUpdateRecord record, Path outputReportDir, int attemptNumber) {
        if (transformedDir == null || !Files.exists(transformedDir)) {
            log.warn("Transformed directory does not exist: {}", transformedDir);
            return null;
        }

        if (projectDir == null || !Files.exists(projectDir)) {
            log.warn("Project directory does not exist: {}", projectDir);
            return null;
        }

        try {
            // 1. Find all transformed files
            List<TransformedFileInfo> transformedFiles = findTransformedFiles(transformedDir);
            
            if (transformedFiles.isEmpty()) {
                log.warn("No transformed files found in: {}", transformedDir);
                return null;
            }

            log.info("Found {} transformed file(s) to replace", transformedFiles.size());

            // 2. Check for differences before replacing
            boolean hasDiff = false;
            int filesWithDiff = 0;
            for (TransformedFileInfo fileInfo : transformedFiles) {
                Path targetFile = fileInfo.getTargetFile(projectDir);
                if (targetFile != null && Files.exists(targetFile)) {
                    if (filesAreDifferent(fileInfo.transformedFile, targetFile)) {
                        hasDiff = true;
                        filesWithDiff++;
                    }
                } else {
                    // New file, consider it as having diff
                    hasDiff = true;
                    filesWithDiff++;
                }
            }

            // If no differences found, return NO_DIFF result
            if (!hasDiff) {
                log.info("No differences found between transformed and original files. All {} files are identical.", 
                        transformedFiles.size());
                Path logFile = outputReportDir.resolve("attempt_" + attemptNumber + "_build.log");
                Attempt attempt = new Attempt(attemptNumber, FailureCategory.UNKNOWN_FAILURE, 
                        outputReportDir.toString(), false);
                // Note: We use UNKNOWN_FAILURE to represent NO_DIFF since the enum doesn't have that category
                return new BuildResult(logFile, FailureCategory.UNKNOWN_FAILURE, attempt, false);
            }

            log.info("Found differences in {}/{} files. Proceeding with replacement.", 
                    filesWithDiff, transformedFiles.size());

            // 3. Replace original files with transformed files
            int replacedCount = 0;
            for (TransformedFileInfo fileInfo : transformedFiles) {
                Path targetFile = fileInfo.getTargetFile(projectDir);
                if (targetFile != null && replaceFile(fileInfo.transformedFile, targetFile)) {
                    replacedCount++;
                    if (verbose) {
                        log.info("Replaced: {} -> {}", fileInfo.transformedFile, targetFile);
                    }
                }
            }

            log.info("Replaced {}/{} files in project", replacedCount, transformedFiles.size());

            if (replacedCount == 0) {
                log.warn("No files were replaced, skipping build");
                return null;
            }

            // 4. Execute build in Docker
            log.info("Starting Docker build with transformed files (image: {}, attempt: {})...", dockerImage, attemptNumber);
            
            // The log file will be saved directly to the report directory with attempt number
            Path buildLogFile = outputReportDir.resolve("attempt_" + attemptNumber + "_build.log");
            Files.createDirectories(buildLogFile.getParent());

            // Get normalized project path in container
            String containerProjectPath = normalizeContainerProjectPath(record.project());

            Result result = dockerBuild.reproduceWithMount(
                    dockerImage,
                    FailureCategory.COMPILATION_FAILURE, // Default category for transformed build
                    projectDir,
                    buildLogFile, // Log will be saved directly to report directory
                    containerProjectPath
            );

            // The log file is already in the report directory (buildLogFile = outputReportDir/build-output.log)
            // reproduceWithMount copies the log from the container to buildLogFile
            if (!Files.exists(buildLogFile)) {
                log.warn("Build log file was not created at: {}", buildLogFile);
                return null;
            }

            log.info("Build output saved to: {}", buildLogFile);

            // 5. Check if build was successful from Result first
            boolean buildSuccessful = result != null && 
                    result.getAttempts().stream().anyMatch(attempt -> 
                            attempt.getFailureCategory() == FailureCategory.BUILD_SUCCESS);
            
            FailureCategory inferredCategory;
            Attempt attempt = null;
            
            if (buildSuccessful) {
                // Build was successful, use BUILD_SUCCESS directly
                inferredCategory = FailureCategory.BUILD_SUCCESS;
                log.info("Build was successful (exit code 0), category: BUILD_SUCCESS");
                
                // Use the attempt from result if available, but update attempt number
                attempt = new Attempt(attemptNumber, FailureCategory.BUILD_SUCCESS, 
                        buildLogFile.getParent().toString(), true);
            } else {
                // 6. Build failed, run breaking-classifier to determine the failure category
                log.info("Build failed, running breaking-classifier to determine category (attempt {})...", attemptNumber);
                BreakingClassifierApp classifierApp = new BreakingClassifierApp();
                Path classifierReportPath = outputReportDir.resolve("attempt_" + attemptNumber + "_breaking-classifier-report.json");
                
                inferredCategory = FailureCategory.UNKNOWN_FAILURE;
                
                try {
                    BreakingReport breakingReport = classifierApp.analyzeLog(buildLogFile, classifierReportPath);
                    
                    if (breakingReport != null) {
                        // Convert breaking-classifier category to se.kth.models.FailureCategory
                        inferredCategory = convertCategory(breakingReport.failureCategory());
                        log.info("Transformed build category determined (attempt {}): {}", attemptNumber, inferredCategory);
                    }
                } catch (Exception e) {
                    log.error("Error running breaking-classifier on transformed build log (attempt {}): {}", 
                            attemptNumber, e.getMessage(), e);
                }

                // 7. Create Attempt from result or inferred category
                attempt = new Attempt(attemptNumber, inferredCategory, 
                        buildLogFile.getParent().toString(), 
                        inferredCategory == FailureCategory.BUILD_SUCCESS);
            }

            log.info("Build completed - Success: {}, Category: {}", buildSuccessful, inferredCategory);
            
            return new BuildResult(buildLogFile, inferredCategory, attempt, buildSuccessful);

        } catch (Exception e) {
            log.error("Error replacing transformed files and building: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * Finds all transformed files recursively in the transformed directory.
     * Returns a list with both the transformed file path and the target path in the project.
     */
    private List<TransformedFileInfo> findTransformedFiles(Path transformedDir) throws IOException {
        List<TransformedFileInfo> files = new ArrayList<>();
        
        if (!Files.exists(transformedDir)) {
            return files;
        }

        Files.walkFileTree(transformedDir, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (file.toString().endsWith(".java")) {
                    // Calculate relative path from transformedDir
                    Path relativePath = transformedDir.relativize(file);
                    
                    // The target file in the project should match the relative path structure
                    // transformed/xdev/tableexport/export/ReportBuilder.java 
                    // -> project/src/main/java/xdev/tableexport/export/ReportBuilder.java
                    TransformedFileInfo info = new TransformedFileInfo(file, relativePath);
                    files.add(info);
                }
                return FileVisitResult.CONTINUE;
            }
        });

        return files;
    }

    /**
     * Checks if two files have different content.
     * 
     * @param file1 first file to compare
     * @param file2 second file to compare
     * @return true if files are different, false if they are identical
     */
    private boolean filesAreDifferent(Path file1, Path file2) {
        if (!Files.exists(file1) || !Files.exists(file2)) {
            return true; // If either file doesn't exist, consider them different
        }
        
        try {
            byte[] content1 = Files.readAllBytes(file1);
            byte[] content2 = Files.readAllBytes(file2);
            return !java.util.Arrays.equals(content1, content2);
        } catch (IOException e) {
            log.warn("Error comparing files {} and {}: {}", file1, file2, e.getMessage());
            return true; // On error, assume different to be safe
        }
    }

    /**
     * Replaces a file in the project with the transformed version.
     */
    private boolean replaceFile(Path transformedFile, Path targetFile) {
        if (targetFile == null || !Files.exists(targetFile)) {
            log.warn("Target file does not exist: {}", targetFile);
            return false;
        }

        try {
            // Create backup of original file
            Path backupFile = targetFile.resolveSibling(targetFile.getFileName().toString() + ".backup");
            Files.copy(targetFile, backupFile, StandardCopyOption.REPLACE_EXISTING);
            
            // Replace with transformed file
            Files.copy(transformedFile, targetFile, StandardCopyOption.REPLACE_EXISTING);
            log.debug("Replaced file: {} (backup: {})", targetFile, backupFile);
            return true;
        } catch (IOException e) {
            log.error("Failed to replace file {}: {}", targetFile, e.getMessage());
            return false;
        }
    }

    /**
     * Normalizes container project path.
     */
    private String normalizeContainerProjectPath(String projectPath) {
        if (projectPath == null || projectPath.trim().isEmpty()) {
            return "/project";
        }
        String normalized = projectPath.trim();
        if (!normalized.startsWith("/")) {
            normalized = "/" + normalized;
        }
        return normalized;
    }

    /**
     * Information about a transformed file and its target location.
     */
    private static class TransformedFileInfo {
        final Path transformedFile;
        final Path relativePath;

        TransformedFileInfo(Path transformedFile, Path relativePath) {
            this.transformedFile = transformedFile;
            this.relativePath = relativePath;
        }

        /**
         * Resolves the target file path in the project directory.
         * Tries common source directory structures.
         */
        Path getTargetFile(Path projectDir) {
            // Try common source locations
            String[] sourceDirs = {"src/main/java", "src/java", "src", ""};
            for (String sourceDir : sourceDirs) {
                Path candidate;
                if (sourceDir.isEmpty()) {
                    candidate = projectDir.resolve(relativePath);
                } else {
                    candidate = projectDir.resolve(sourceDir).resolve(relativePath);
                }
                if (Files.exists(candidate)) {
                    return candidate;
                }
            }
            // Fallback: try to find file by name only (last component)
            String fileName = relativePath.getFileName().toString();
            try {
                return Files.walk(projectDir)
                        .filter(p -> p.getFileName().toString().equals(fileName))
                        .filter(Files::isRegularFile)
                        .findFirst()
                        .orElse(null);
            } catch (IOException e) {
                log.warn("Error searching for file {} in project: {}", fileName, e.getMessage());
            }
            // Last fallback: use src/main/java structure (create if needed)
            return projectDir.resolve("src/main/java").resolve(relativePath);
        }
    }

    /**
     * Converts breaking-classifier FailureCategory to se.kth.models.FailureCategory.
     */
    private FailureCategory convertCategory(github.chains.breakingclassifier.FailureCategory classifierCategory) {
        if (classifierCategory == null) {
            return FailureCategory.UNKNOWN_FAILURE;
        }

        try {
            return switch (classifierCategory) {
                case JAVA_VERSION_FAILURE -> FailureCategory.JAVA_VERSION_FAILURE;
                case TEST_FAILURE -> FailureCategory.TEST_FAILURE;
                case WERROR_FAILURE -> FailureCategory.WERROR_FAILURE;
                case COMPILATION_FAILURE -> FailureCategory.COMPILATION_FAILURE;
                case BUILD_SUCCESS -> FailureCategory.BUILD_SUCCESS;
                case ENFORCER_FAILURE -> FailureCategory.ENFORCER_FAILURE;
                case DEPENDENCY_RESOLUTION_FAILURE -> FailureCategory.DEPENDENCY_RESOLUTION_FAILURE;
                case DEPENDENCY_LOCK_FAILURE -> FailureCategory.DEPENDENCY_LOCK_FAILURE;
                case UNKNOWN -> FailureCategory.UNKNOWN_FAILURE;
            };
        } catch (Exception e) {
            log.warn("Failed to convert category: {}", classifierCategory, e);
            return FailureCategory.UNKNOWN_FAILURE;
        }
    }
}

