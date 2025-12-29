package com.example.core.service;

import com.example.core.model.BreakingUpdateRecord;
import com.example.core.model.ClassificationSummary;
import com.example.core.model.UpdatedDependency;
import com.example.core.pipeline.ProjectLogLocator;
import com.example.core.util.FileSystemUtils;
import com.example.core.util.ProjectPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.kth.DockerBuild;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;

/**
 * Service class responsible for extracting projects from Docker images
 * and managing the extraction workflow.
 */
public class BreakingUpdateExtractionService {

    private static final Logger log = LoggerFactory.getLogger(BreakingUpdateExtractionService.class);

    private final boolean verbose;
    private final DockerBuild dockerBuild;
    private final GitWorkflowService gitWorkflowService;

    public BreakingUpdateExtractionService(boolean verbose) {
        this.verbose = verbose;
        this.dockerBuild = new DockerBuild(false, verbose);
        this.gitWorkflowService = new GitWorkflowService();
    }

    /**
     * Removes existing {breakingCommit} folders before processing.
     * This is done BEFORE any Docker processing starts.
     *
     * @param records       the list of breaking update records
     * @param outputBaseDir the base output directory
     */
    public void cleanExistingFolders(List<BreakingUpdateRecord> records, Path outputBaseDir) {
        int removedCount = 0;
        int failedCount = 0;

        for (BreakingUpdateRecord record : records) {
            String breakingCommit = record.breakingCommit();
            if (breakingCommit == null || breakingCommit.trim().isEmpty()) {
                continue;
            }

            Path breakingCommitDir = outputBaseDir.resolve(breakingCommit);
            if (Files.exists(breakingCommitDir) && Files.isDirectory(breakingCommitDir)) {
                try {
                    if (verbose) {
                        System.out.println("  Removing: " + breakingCommitDir);
                    }
                    FileSystemUtils.deleteDirectory(breakingCommitDir);
                    removedCount++;
                    log.info("Removed existing folder: {}", breakingCommitDir);
                } catch (IOException e) {
                    failedCount++;
                    log.warn("Failed to remove existing folder: {}", breakingCommitDir, e);
                    if (verbose) {
                        System.out.println("  ⚠ Failed to remove: " + breakingCommitDir + " - " + e.getMessage());
                    }
                }
            }
        }

        System.out.println("Cleaned " + removedCount + " existing folder(s)");
        if (failedCount > 0) {
            System.out.println("Failed to remove " + failedCount + " folder(s)");
        }
        System.out.println();
    }

    /**
     * Extracts projects from Docker images for each BreakingUpdateRecord.
     *
     * @param records                the list of breaking update records
     * @param outputBaseDir          the base output directory
     * @param extractJarsAndClassify whether to extract JARs and run
     *                               breaking-classifier
     * @param cleanExisting          whether clean mode was enabled (folders already
     *                               removed before this method)
     */
    public List<ClassificationSummary> extractProjectsFromDockerImages(
            List<BreakingUpdateRecord> records,
            Path outputBaseDir,
            boolean extractJarsAndClassify,
            boolean cleanExisting,
            Consumer<ClassificationSummary> summaryConsumer) {

        int successCount = 0;
        int failureCount = 0;

        JarExtractionService jarService = new JarExtractionService(verbose, dockerBuild);
        ClassificationService classificationService = new ClassificationService(verbose);
        List<ClassificationSummary> summaries = new java.util.ArrayList<>();

        for (int i = 0; i < records.size(); i++) {
            BreakingUpdateRecord record = records.get(i);
            ClassificationOutcome classificationOutcome = null;
            String dockerImage = null;

            try {
                // Extract Docker image from reproduction command
                dockerImage = dockerBuild.extractDockerImageFromCommand(record.breakingUpdateReproductionCommand());

                if (dockerImage == null || dockerImage.trim().isEmpty()) {
                    log.warn("Could not extract Docker image from record {}: {}", i + 1, record.descriptor());
                    if (verbose) {
                        System.out.println("Skipping record " + (i + 1) + ": Could not extract Docker image");
                    }
                    failureCount++;
                    continue;
                }

                // Get project name and path inside container
                String projectName = record.project();
                if (projectName == null || projectName.trim().isEmpty()) {
                    log.warn("Project name is null or empty for record {}: {}", i + 1, record.descriptor());
                    if (verbose) {
                        System.out.println("Skipping record " + (i + 1) + ": Project name is missing");
                    }
                    failureCount++;
                    continue;
                }

                // Project path inside container is /{projectName}
                String projectPath = "/" + projectName;
                String breakingCommit = record.breakingCommit();

                if (breakingCommit == null || breakingCommit.trim().isEmpty()) {
                    log.warn("Breaking commit is null or empty for record {}: {}", i + 1, record.descriptor());
                    if (verbose) {
                        System.out.println("Skipping record " + (i + 1) + ": Breaking commit is missing");
                    }
                    failureCount++;
                    continue;
                }

                if (verbose) {
                    System.out.println("\nExtracting project " + (i + 1) + "/" + records.size() + ":");
                    System.out.println("  Project: " + projectName);
                    System.out.println("  Breaking Commit: " + breakingCommit);
                    System.out.println("  Docker Image: " + dockerImage);
                    System.out.println("  Project Path in Container: " + projectPath);
                }

                // Check if project already exists (only if clean mode is NOT enabled)
                Path breakingCommitDir = outputBaseDir.resolve(breakingCommit);
                boolean projectExists = !cleanExisting && Files.exists(breakingCommitDir)
                        && Files.isDirectory(breakingCommitDir);

                // Check if all required components exist
                boolean allComponentsExist = false;
                if (projectExists && !cleanExisting) {
                    UpdatedDependency updatedDependency = record.updatedDependency();
                    allComponentsExist = checkAllComponentsExist(
                            breakingCommitDir,
                            record.project(),
                            updatedDependency,
                            extractJarsAndClassify);
                }

                Path extractedPath;
                boolean skippedExtraction = false;

                // Skip all extraction if all components exist and clean is false
                if (allComponentsExist && !cleanExisting) {
                    extractedPath = breakingCommitDir;
                    skippedExtraction = true;
                    if (verbose) {
                        System.out.println("  All components exist, skipping extraction:");
                        System.out.println("    - Project: exists");
                        System.out.println("    - M2: exists");
                        if (extractJarsAndClassify && record.updatedDependency() != null) {
                            UpdatedDependency dep = record.updatedDependency();
                            Path prevJar = breakingCommitDir
                                    .resolve("%s-%s.jar".formatted(dep.dependencyArtifactId(), dep.previousVersion()));
                            Path newJar = breakingCommitDir
                                    .resolve("%s-%s.jar".formatted(dep.dependencyArtifactId(), dep.newVersion()));
                            System.out.println("    - Previous JAR: " + (Files.exists(prevJar) ? "exists" : "missing"));
                            System.out.println("    - New JAR: " + (Files.exists(newJar) ? "exists" : "missing"));
                        }
                    } else {
                        System.out
                                .println((i + 1) + ". ⊙ Using existing: " + projectName + " (" + breakingCommit + ")");
                    }
                    log.info("Skipping extraction for existing project with all components: {}", breakingCommit);
                } else if (projectExists && !cleanExisting) {
                    // Project exists but some components are missing, extract from Docker
                    extractedPath = dockerBuild.extractProjectAndM2FromImage(
                            dockerImage,
                            projectPath,
                            outputBaseDir,
                            breakingCommit,
                            false // Don't force overwrite
                    );
                } else {
                    // Extract project and m2 folder from Docker
                    extractedPath = dockerBuild.extractProjectAndM2FromImage(
                            dockerImage,
                            projectPath,
                            outputBaseDir,
                            breakingCommit,
                            false // Don't force overwrite
                    );
                }

                if (extractedPath != null && Files.exists(extractedPath)) {
                    successCount++;
                    if (verbose) {
                        System.out.println("  ✓ Successfully extracted to: " + extractedPath);
                    } else {
                        System.out.println((i + 1) + ". ✓ Extracted: " + projectName + " (" + breakingCommit + ")");
                    }

                    // Initialize Git repo (branch will be created by repair pipeline)
                    try {
                        Path projectDir = ProjectPaths.resolveProjectDir(extractedPath, projectName);
                        gitWorkflowService.initAndCommit(projectDir, "Initial extraction from " + dockerImage);
                    } catch (Exception e) {
                        log.warn("Failed to initialize git repo for {}: {}", projectName, e.getMessage());
                        if (verbose) {
                            e.printStackTrace();
                        }
                    }

                    // Extract JARs and run breaking-classifier if requested
                    if (extractJarsAndClassify) {
                        if (skippedExtraction) {
                            // Only run classifier, skip JAR extraction since they already exist
                            classificationOutcome = classificationService.runClassifierOnly(record, extractedPath,
                                    breakingCommit);
                        } else {
                            // Extract JARs and run classifier
                            classificationOutcome = jarService.extractJarsAndRunClassifier(
                                    record,
                                    dockerImage,
                                    extractedPath,
                                    breakingCommit);
                        }
                    }
                } else {
                    failureCount++;
                    log.error("Failed to extract project for record {}: {}", i + 1, record.descriptor());
                    if (verbose) {
                        System.out.println("  ✗ Failed to extract project");
                    } else {
                        System.out.println((i + 1) + ". ✗ Failed: " + projectName + " (" + breakingCommit + ")");
                    }
                }

            } catch (Exception e) {
                failureCount++;
                log.error("Error extracting project for record {}: {}", i + 1, record.descriptor(), e);
                if (verbose) {
                    System.out.println("  ✗ Error: " + e.getMessage());
                    e.printStackTrace();
                } else {
                    System.out.println((i + 1) + ". ✗ Error: " + record.project() + " - " + e.getMessage());
                }
            } finally {
                ClassificationSummary summary = buildSummary(record, dockerImage, classificationOutcome);
                summaries.add(summary);
                if (summaryConsumer != null) {
                    summaryConsumer.accept(summary);
                }
                if (dockerImage != null) {
                    DockerBuild.deleteImage(dockerImage);
                }
            }
        }

        System.out.println("\n=== Extraction Summary ===");
        System.out.println("Successfully extracted: " + successCount);
        System.out.println("Failed: " + failureCount);
        System.out.println("Total: " + records.size());
        return summaries;
    }

    /**
     * Runs breaking-classifier for projects that already exist on disk without
     * re-downloading from Docker.
     *
     * @param records       records to classify
     * @param outputBaseDir base output directory containing {breakingCommit}
     *                      folders
     * @return summaries with dataset and inferred categories
     */
    public List<ClassificationSummary> classifyExistingProjects(
            List<BreakingUpdateRecord> records,
            Path outputBaseDir,
            Consumer<ClassificationSummary> summaryConsumer) {
        ClassificationService classificationService = new ClassificationService(verbose);
        List<ClassificationSummary> summaries = new java.util.ArrayList<>();

        System.out.println("\n=== Classifying Existing Projects ===");
        for (BreakingUpdateRecord record : records) {
            ClassificationOutcome outcome = null;
            Path commitDir = outputBaseDir.resolve(record.breakingCommit());
            String dockerImage = null;

            try {
                // Extract Docker image from reproduction command (needed for repair pipeline)
                // Only parse the command string, don't perform actual Docker extraction
                if (record.breakingUpdateReproductionCommand() != null && 
                    !record.breakingUpdateReproductionCommand().trim().isEmpty()) {
                    dockerImage = dockerBuild.extractDockerImageFromCommand(record.breakingUpdateReproductionCommand());
                }

                if (!Files.exists(commitDir) || !Files.isDirectory(commitDir)) {
                    log.warn("Commit directory does not exist for {}: {}", record.descriptor(), commitDir);
                    if (verbose) {
                        System.out.println("  ⚠ Missing directory for " + record.breakingCommit());
                    }
                    summaries.add(buildSummary(record, dockerImage, outcome));
                    continue;
                }

                Path logFile = ProjectLogLocator.findLogFile(commitDir, record.project(), record.breakingCommit());
                if (logFile == null || !Files.exists(logFile)) {
                    log.warn("Log file not found for existing record {} under {}", record.descriptor(), commitDir);
                    if (verbose) {
                        System.out.println("  ⚠ Log file not found under " + commitDir);
                    }
                } else {
                    outcome = classificationService.runClassifier(logFile, commitDir);
                }
            } catch (Exception e) {
                log.error("Error classifying existing project for {}: {}", record.descriptor(), e.getMessage());
                if (verbose) {
                    System.out.println("  ✗ Error: " + e.getMessage());
                }
            } finally {
                ClassificationSummary summary = buildSummary(record, dockerImage, outcome);
                summaries.add(summary);
                if (summaryConsumer != null) {
                    summaryConsumer.accept(summary);
                }
            }
        }

        return summaries;
    }

    /**
     * Checks if all required components exist (project, m2, and JARs if needed).
     *
     * @param breakingCommitDir      the breaking commit directory
     * @param updatedDependency      the updated dependency information
     * @param extractJarsAndClassify whether JARs are needed
     * @return true if all required components exist, false otherwise
     */
    private boolean checkAllComponentsExist(Path breakingCommitDir,
            String projectName,
            UpdatedDependency updatedDependency,
            boolean extractJarsAndClassify) {
        // Check project folder
        Path projectDir = ProjectPaths.resolveProjectDir(breakingCommitDir, projectName);
        if (!Files.exists(projectDir) || !Files.isDirectory(projectDir)) {
            return false;
        }

        // Check m2 folder
        Path m2Dir = breakingCommitDir.resolve("m2");
        if (!Files.exists(m2Dir) || !Files.isDirectory(m2Dir)) {
            return false;
        }

        // Check JARs if needed
        if (extractJarsAndClassify && updatedDependency != null) {
            String artifactId = updatedDependency.dependencyArtifactId();
            String previousVersion = updatedDependency.previousVersion();
            String newVersion = updatedDependency.newVersion();

            boolean hasPreviousJar = true;
            boolean hasNewJar = true;

            if (previousVersion != null && !previousVersion.trim().isEmpty()) {
                Path previousJar = breakingCommitDir.resolve("%s-%s.jar".formatted(artifactId, previousVersion));
                hasPreviousJar = Files.exists(previousJar);
            }

            if (newVersion != null && !newVersion.trim().isEmpty()) {
                Path newJar = breakingCommitDir.resolve("%s-%s.jar".formatted(artifactId, newVersion));
                hasNewJar = Files.exists(newJar);
            }

            // Both JARs should exist if versions are provided
            if ((previousVersion != null && !previousVersion.trim().isEmpty() && !hasPreviousJar) ||
                    (newVersion != null && !newVersion.trim().isEmpty() && !hasNewJar)) {
                return false;
            }
        }

        return true;
    }

    private ClassificationSummary buildSummary(BreakingUpdateRecord record,
            String dockerImage,
            ClassificationOutcome outcome) {
        String datasetCategory = record.failureCategory();
        String inferredCategory = null;
        String logFile = null;
        String classifierReport = null;

        if (outcome != null) {
            if (outcome.logFile() != null) {
                logFile = outcome.logFile().toString();
            }
            if (outcome.reportJson() != null) {
                classifierReport = outcome.reportJson().toString();
            }
            if (outcome.report() != null && outcome.report().failureCategory() != null) {
                inferredCategory = outcome.report().failureCategory().name();
            }
        }

        return new ClassificationSummary(
                record.project(),
                record.breakingCommit(),
                datasetCategory,
                inferredCategory,
                logFile,
                classifierReport,
                dockerImage,
                null // attempts
        );
    }
}
