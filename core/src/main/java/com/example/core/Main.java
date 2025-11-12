package com.example.core;

import com.example.core.model.BreakingUpdateRecord;
import com.example.core.model.UpdatedDependency;
import com.example.core.pipeline.FailureCategoryUtils;
import com.example.core.report.JsonReportReader;
import github.chains.breakingclassifier.BreakingReport;
import github.chains.breakingclassifier.ErrorReportAggregator;
import github.chains.breakingclassifier.MavenErrorExtractor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.kth.DockerBuild;
import se.kth.models.FailureCategory;

import java.util.Map;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Main class for processing breaking update records from JSON files.
 * Reads JSON files from a folder and extracts BreakingUpdateRecord information.
 */
public class Main {

    private static final Logger log = LoggerFactory.getLogger(Main.class);

    // Default configuration values - update these with your actual paths
    private static final String DEFAULT_INPUT_DIR = "/Users/frankreyesgarcia/Documents/WORK/PHD/Bump/bump/data/benchmark";
    private static final String DEFAULT_OUTPUT_DIR = "output";
    private static final String DEFAULT_CATEGORY = "COMPILATION_FAILURE"; // null = no filter, or "COMPILATION_FAILURE", "TEST_FAILURE", etc.
    private static final boolean DEFAULT_EXTRACT_PROJECTS = true; // Set to true to extract projects from Docker images
    private static final boolean DEFAULT_EXTRACT_JARS_AND_CLASSIFY = true; // Set to true to extract JARs and run breaking-classifier
    private static final boolean DEFAULT_CLEAN_EXISTING = true; // Set to true to remove existing {breakingCommit} folders before extraction

    public static void main(String[] args) {
        // Parse command line arguments
        String inputDirStr = DEFAULT_INPUT_DIR;
        String outputDirStr = DEFAULT_OUTPUT_DIR;
        boolean verbose = false;
        boolean extractProjects = DEFAULT_EXTRACT_PROJECTS;
        boolean extractJarsAndClassify = DEFAULT_EXTRACT_JARS_AND_CLASSIFY;
        boolean cleanExisting = DEFAULT_CLEAN_EXISTING;
        String singleJsonFile = "0abf7148300f40a1da0538ab060552bca4a2f1d8"; // Name of JSON file without extension to process
        FailureCategory filterCategory = FailureCategoryUtils.parseFailureCategory(DEFAULT_CATEGORY);

        // Parse arguments
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--input", "-i" -> {
                    if (i + 1 < args.length) {
                        inputDirStr = args[++i];
                    } else {
                        System.err.println("Error: --input requires a directory path");
                        printUsage();
                        System.exit(1);
                        return;
                    }
                }
                case "--output", "-o" -> {
                    if (i + 1 < args.length) {
                        outputDirStr = args[++i];
                    } else {
                        System.err.println("Error: --output requires a directory path");
                        printUsage();
                        System.exit(1);
                        return;
                    }
                }
                case "--category", "-c" -> {
                    if (i + 1 < args.length) {
                        try {
                            filterCategory = FailureCategoryUtils.parseFailureCategory(args[++i]);
                            if (filterCategory == FailureCategory.UNKNOWN_FAILURE && 
                                !args[i].equalsIgnoreCase("UNKNOWN_FAILURE")) {
                                System.err.println("Warning: Invalid category '" + args[i] + 
                                    "'. Use --help to see available categories.");
                            }
                        } catch (Exception e) {
                            System.err.println("Error: Invalid failure category: " + args[i]);
                            printUsage();
                            System.exit(1);
                            return;
                        }
                    } else {
                        System.err.println("Error: --category requires a category name");
                        printUsage();
                        System.exit(1);
                        return;
                    }
                }
                case "--verbose", "-v" -> verbose = true;
                case "--extract", "-e" -> extractProjects = true;
                case "--no-extract" -> extractProjects = false;
                case "--classify", "-k" -> extractJarsAndClassify = true;
                case "--no-classify" -> extractJarsAndClassify = false;
                case "--clean" -> cleanExisting = true;
                case "--no-clean" -> cleanExisting = false;
                case "--file", "-f" -> {
                    if (i + 1 < args.length) {
                        singleJsonFile = args[++i];
                    } else {
                        System.err.println("Error: --file requires a JSON filename (without extension)");
                        printUsage();
                        System.exit(1);
                        return;
                    }
                }
                case "--help", "-h" -> {
                    printUsage();
                    System.exit(0);
                    return;
                }
                default -> {
                    // If no flag, assume it's the input directory (for convenience)
                    if (inputDirStr.equals(DEFAULT_INPUT_DIR) && !args[i].startsWith("-")) {
                        inputDirStr = args[i];
                    } else {
                        System.err.println("Unknown option: " + args[i]);
                        printUsage();
                        System.exit(1);
                        return;
                    }
                }
            }
        }

        Path inputDir = Paths.get(inputDirStr);
        Path outputDir = Paths.get(outputDirStr);

        log.info("=== Breaking Update Record Extractor ===");
        log.info("Input directory: {}", inputDir);
        if (verbose) {
            log.info("Output directory: {}", outputDir);
        }
        if (filterCategory != null) {
            log.info("Filter category: {}", filterCategory);
            System.out.println("Filtering by category: " + filterCategory);
        }
        if (singleJsonFile != null) {
            log.info("Processing single JSON file: {}", singleJsonFile);
            System.out.println("Processing single file: " + singleJsonFile + ".json");
        }

        // Validate input directory
        if (!Files.exists(inputDir) || !Files.isDirectory(inputDir)) {
            log.error("Input directory does not exist or is not a directory: {}", inputDir);
            System.err.println("Error: Input directory does not exist: " + inputDir);
            printUsage();
            System.exit(1);
            return;
        }

        // Read and process JSON files
        try {
            JsonReportReader reader = new JsonReportReader();
            List<BreakingUpdateRecord> records;
            
            if (singleJsonFile != null) {
                // Read only the specified JSON file
                Path jsonFilePath = inputDir.resolve(singleJsonFile + ".json");
                if (!Files.exists(jsonFilePath)) {
                    log.error("JSON file does not exist: {}", jsonFilePath);
                    System.err.println("Error: JSON file does not exist: " + jsonFilePath);
                    System.exit(1);
                    return;
                }
                records = reader.readFromFile(jsonFilePath);
            } else {
                // Read all JSON files from directory
                records = reader.readFromDirectory(inputDir);
            }

            log.info("Found {} breaking update records", records.size());
            
            // Filter by category if specified
            if (filterCategory != null) {
                final FailureCategory finalFilterCategory = filterCategory; // Make final for lambda
                int originalSize = records.size();
                records = records.stream()
                        .filter(record -> FailureCategoryUtils.matchesFailureCategory(record, finalFilterCategory))
                        .collect(Collectors.toList());
                log.info("Filtered to {} records matching category: {}", records.size(), filterCategory);
                System.out.println("\n=== Breaking Update Records ===");
                System.out.println("Total records found: " + originalSize);
                System.out.println("Records matching category '" + filterCategory + "': " + records.size());
            } else {
                System.out.println("\n=== Breaking Update Records ===");
                System.out.println("Total records found: " + records.size());
            }
            System.out.println();

            // Display information from each record
            for (int i = 0; i < records.size(); i++) {
                BreakingUpdateRecord record = records.get(i);
                if (verbose) {
                    displayRecordInfo(i + 1, record);
                } else {
                    // Simple output for non-verbose mode
                    System.out.println((i + 1) + ". " + record.project() + " - " + record.breakingCommit());
                }
            }
            
            if (!verbose && records.size() > 0) {
                System.out.println("\nUse --verbose to see detailed information for each record");
            }

            // Extract projects from Docker images if requested
            if (extractProjects) {
                log.info("=== Starting Project Extraction ===");
                System.out.println("\n=== Extracting Projects from Docker Images ===");
                
                // If clean mode is enabled, remove all existing folders BEFORE processing
                if (cleanExisting) {
                    System.out.println("Clean mode enabled: Removing existing {breakingCommit} folders before processing...");
                    cleanExistingFolders(records, outputDir, verbose);
                }
                
                extractProjectsFromDockerImages(records, outputDir, verbose, extractJarsAndClassify, cleanExisting);
            }

            log.info("=== Processing Complete ===");
            System.out.println("\n=== Processing Complete ===");

        } catch (IOException e) {
            log.error("Error reading breaking update records from {}", inputDir, e);
            System.err.println("Error: Failed to read breaking update records: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    /**
     * Displays information from a BreakingUpdateRecord.
     *
     * @param index the record index
     * @param record the breaking update record
     */
    private static void displayRecordInfo(int index, BreakingUpdateRecord record) {
        System.out.println("--- Record #" + index + " ---");
        System.out.println("Project: " + (record.project() != null ? record.project() : "N/A"));
        System.out.println("Breaking Commit: " + (record.breakingCommit() != null ? record.breakingCommit() : "N/A"));
        System.out.println("URL: " + (record.url() != null ? record.url() : "N/A"));
        System.out.println("Failure Category: " + (record.failureCategory() != null ? record.failureCategory() : "N/A"));
        
        if (record.updatedDependency() != null) {
            System.out.println("Updated Dependency:");
            System.out.println("  Group ID: " + record.updatedDependency().dependencyGroupId());
            System.out.println("  Artifact ID: " + record.updatedDependency().dependencyArtifactId());
            System.out.println("  Previous Version: " + record.updatedDependency().previousVersion());
            System.out.println("  New Version: " + record.updatedDependency().newVersion());
        }
        
        if (record.breakingUpdateReproductionCommand() != null) {
            System.out.println("Reproduction Command: " + record.breakingUpdateReproductionCommand());
        }
        
        System.out.println();
    }

    /**
     * Removes existing {breakingCommit} folders before processing.
     * This is done BEFORE any Docker processing starts.
     *
     * @param records the list of breaking update records
     * @param outputBaseDir the base output directory
     * @param verbose whether to show verbose output
     */
    private static void cleanExistingFolders(List<BreakingUpdateRecord> records, Path outputBaseDir, boolean verbose) {
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
                    deleteDirectory(breakingCommitDir);
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
     * @param records the list of breaking update records
     * @param outputBaseDir the base output directory
     * @param verbose whether to show verbose output
     * @param extractJarsAndClassify whether to extract JARs and run breaking-classifier
     * @param cleanExisting whether clean mode was enabled (folders already removed before this method)
     */
    private static void extractProjectsFromDockerImages(List<BreakingUpdateRecord> records, Path outputBaseDir, boolean verbose, boolean extractJarsAndClassify, boolean cleanExisting) {
        DockerBuild dockerBuild = new DockerBuild(false);
        int successCount = 0;
        int failureCount = 0;

        for (int i = 0; i < records.size(); i++) {
            BreakingUpdateRecord record = records.get(i);
            
            try {
                // Extract Docker image from reproduction command
                String dockerImage = extractDockerImageFromCommand(record.breakingUpdateReproductionCommand(), dockerBuild);
                
                if (dockerImage == null || dockerImage.trim().isEmpty()) {
                    log.warn("Could not extract Docker image from record {}: {}", i + 1, record.descriptor());
                    if (verbose) {
                        System.out.println("Skipping record " + (i + 1) + ": Could not extract Docker image");
                    }
                    failureCount++;
                    continue;
                }

                // Keep the Docker image name as-is (including "-breaking" suffix if present)

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
                // If clean mode is enabled, folders were already removed before processing
                Path breakingCommitDir = outputBaseDir.resolve(breakingCommit);
                boolean projectExists = !cleanExisting && Files.exists(breakingCommitDir) && Files.isDirectory(breakingCommitDir);
                
                // Check if all required components exist (project, m2, JARs)
                // Only check if clean mode is NOT enabled
                boolean allComponentsExist = false;
                if (projectExists && !cleanExisting) {
                    UpdatedDependency updatedDependency = record.updatedDependency();
                    allComponentsExist = checkAllComponentsExist(
                        breakingCommitDir,
                        updatedDependency,
                        extractJarsAndClassify
                    );
                }

                Path extractedPath;
                boolean extractedFromDocker = false;
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
                            Path prevJar = breakingCommitDir.resolve("%s-%s.jar".formatted(dep.dependencyArtifactId(), dep.previousVersion()));
                            Path newJar = breakingCommitDir.resolve("%s-%s.jar".formatted(dep.dependencyArtifactId(), dep.newVersion()));
                            System.out.println("    - Previous JAR: " + (Files.exists(prevJar) ? "exists" : "missing"));
                            System.out.println("    - New JAR: " + (Files.exists(newJar) ? "exists" : "missing"));
                        }
                    } else {
                        System.out.println((i + 1) + ". ⊙ Using existing: " + projectName + " (" + breakingCommit + ")");
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
                    extractedFromDocker = true;
                } else {
                    // Extract project and m2 folder from Docker
                    extractedPath = dockerBuild.extractProjectAndM2FromImage(
                        dockerImage,
                        projectPath,
                        outputBaseDir,
                        breakingCommit,
                        false // Don't force overwrite
                    );
                    extractedFromDocker = true;
                }

                if (extractedPath != null && Files.exists(extractedPath)) {
                    if (extractedFromDocker) {
                        successCount++;
                        if (verbose) {
                            System.out.println("  ✓ Successfully extracted to: " + extractedPath);
                        } else {
                            System.out.println((i + 1) + ". ✓ Extracted: " + projectName + " (" + breakingCommit + ")");
                        }
                    } else if (skippedExtraction) {
                        // Count as success if we skipped extraction because everything exists
                        successCount++;
                    } else {
                        // Count as success even if we skipped extraction
                        successCount++;
                    }
                    
                    // Extract JARs and run breaking-classifier if requested
                    // This runs regardless of whether we extracted from Docker or used existing
                    if (extractJarsAndClassify) {
                        if (skippedExtraction) {
                            // Only run classifier, skip JAR extraction since they already exist
                            runClassifierOnly(record, extractedPath, breakingCommit, verbose);
                        } else {
                            // Extract JARs and run classifier
                            extractJarsAndRunClassifier(record, dockerImage, extractedPath, breakingCommit, dockerBuild, verbose);
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
            }
        }

        System.out.println("\n=== Extraction Summary ===");
        System.out.println("Successfully extracted: " + successCount);
        System.out.println("Failed: " + failureCount);
        System.out.println("Total: " + records.size());
    }

    /**
     * Checks if all required components exist (project, m2, and JARs if needed).
     *
     * @param breakingCommitDir the breaking commit directory
     * @param updatedDependency the updated dependency information
     * @param extractJarsAndClassify whether JARs are needed
     * @return true if all required components exist, false otherwise
     */
    private static boolean checkAllComponentsExist(Path breakingCommitDir, UpdatedDependency updatedDependency, boolean extractJarsAndClassify) {
        // Check project folder
        Path projectDir = breakingCommitDir.resolve("project");
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

    /**
     * Runs only the breaking-classifier without extracting JARs (assumes JARs already exist).
     *
     * @param record the breaking update record
     * @param extractedPath the path where the project was extracted
     * @param breakingCommit the breaking commit hash
     * @param verbose whether to show verbose output
     */
    private static void runClassifierOnly(BreakingUpdateRecord record, Path extractedPath, String breakingCommit, boolean verbose) {
        try {
            if (verbose) {
                System.out.println("  Running analysis only (JARs already exist)");
            }

            // Find log file in project folder (inside project/{project}/)
            Path projectDir = extractedPath.resolve("project");
            String projectName = record.project();
            Path logFile = findLogFile(projectDir, projectName, breakingCommit);

            if (logFile == null || !Files.exists(logFile)) {
                Path expectedPath = projectDir.resolve(projectName != null ? projectName : "").resolve(breakingCommit + ".log");
                log.warn("Log file not found for record {}: expected at {}", record.descriptor(), expectedPath);
                if (verbose) {
                    System.out.println("  ⚠ Log file not found: " + expectedPath);
                }
                return;
            }

            if (verbose) {
                System.out.println("  Running breaking-classifier on: " + logFile);
            }

            // Run breaking-classifier
            BreakingReport report = runBreakingClassifier(logFile);

            if (report != null) {
                github.chains.breakingclassifier.FailureCategory category = report.failureCategory();
                System.out.println("  Category: " + category);
                if (verbose) {
                    System.out.println("  Original failure path: " + report.originalFailurePath());
                    System.out.println("  Errors found: " + report.errorsByFile().size());
                }
            } else {
                log.warn("Breaking-classifier returned null for record: {}", record.descriptor());
                if (verbose) {
                    System.out.println("  ⚠ Breaking-classifier returned null");
                }
            }

        } catch (Exception e) {
            log.error("Error running classifier for record: {}", record.descriptor(), e);
            if (verbose) {
                System.out.println("  ✗ Error: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    /**
     * Extracts JARs from Docker image and runs breaking-classifier on the project.
     *
     * @param record the breaking update record
     * @param dockerImage the Docker image name
     * @param extractedPath the path where the project was extracted
     * @param breakingCommit the breaking commit hash
     * @param dockerBuild the DockerBuild instance
     * @param verbose whether to show verbose output
     */
    private static void extractJarsAndRunClassifier(BreakingUpdateRecord record, String dockerImage, Path extractedPath, 
                                                     String breakingCommit, DockerBuild dockerBuild, boolean verbose) {
        try {
            UpdatedDependency updatedDependency = record.updatedDependency();
            if (updatedDependency == null) {
                log.warn("No updated dependency information for record: {}", record.descriptor());
                if (verbose) {
                    System.out.println("  ⚠ Skipping JAR extraction: No dependency information");
                }
                return;
            }

            // Extract previous JAR from pre Docker image
            Path previousJarPath = null;
            if (updatedDependency.previousVersion() != null && !updatedDependency.previousVersion().trim().isEmpty()) {
                String preDockerImage = extractDockerImageFromCommand(record.preCommitReproductionCommand(), dockerBuild);
                if (preDockerImage != null && !preDockerImage.trim().isEmpty()) {
                    if (verbose) {
                        System.out.println("  Extracting previous JAR from pre Docker image: " + preDockerImage);
                    }
                    previousJarPath = extractPreviousJarFromPreDockerImage(
                        dockerBuild,
                        preDockerImage,
                        updatedDependency.dependencyGroupId(),
                        updatedDependency.dependencyArtifactId(),
                        updatedDependency.previousVersion(),
                        extractedPath,
                        verbose
                    );
                } else {
                    if (verbose) {
                        System.out.println("  ⚠ Could not extract pre Docker image, trying m2 folder");
                    }
                }
            }

            // Find and copy new version JAR from m2 folder
            Path m2Dir = extractedPath.resolve("m2");
            Path newJarPath = null;
            if (updatedDependency.newVersion() != null && !updatedDependency.newVersion().trim().isEmpty()) {
                if (verbose) {
                    System.out.println("  Searching for new JAR in m2 folder: " + m2Dir);
                }
                newJarPath = findAndCopyNewJarFromM2(
                    m2Dir,
                    updatedDependency.dependencyGroupId(),
                    updatedDependency.dependencyArtifactId(),
                    updatedDependency.newVersion(),
                    extractedPath
                );
            }

            // Build jarPaths map
            Map<String, Path> jarPaths = new java.util.HashMap<>();
            if (previousJarPath != null) {
                jarPaths.put("previous", previousJarPath);
            }
            if (newJarPath != null) {
                jarPaths.put("new", newJarPath);
            }

            if (jarPaths != null && !jarPaths.isEmpty()) {
                boolean hasPrevious = jarPaths.containsKey("previous");
                boolean hasNew = jarPaths.containsKey("new");
                
                if (verbose) {
                    System.out.println("  ✓ JARs found and copied:");
                    if (hasPrevious) {
                        System.out.println("    - Previous version: " + jarPaths.get("previous").getFileName());
                    } else {
                        System.out.println("    - Previous version: NOT FOUND");
                    }
                    if (hasNew) {
                        System.out.println("    - New version: " + jarPaths.get("new").getFileName());
                    } else {
                        System.out.println("    - New version: NOT FOUND");
                    }
                } else {
                    StringBuilder jarStatus = new StringBuilder("    ✓ JARs: ");
                    if (hasPrevious && hasNew) {
                        jarStatus.append("both versions");
                    } else if (hasPrevious) {
                        jarStatus.append("previous only");
                    } else if (hasNew) {
                        jarStatus.append("new only");
                    } else {
                        jarStatus.append("none found");
                    }
                    System.out.println(jarStatus.toString());
                }
            } else {
                log.warn("Failed to find any JARs in m2 folder for record: {}", record.descriptor());
                if (verbose) {
                    System.out.println("  ⚠ Failed to find JARs in m2 folder (both previous and new versions)");
                } else {
                    System.out.println("    ⚠ No JARs found");
                }
            }

            // Find log file in project folder (inside project/{project}/)
            Path projectDir = extractedPath.resolve("project");
            String projectName = record.project();
            Path logFile = findLogFile(projectDir, projectName, breakingCommit);

            if (logFile == null || !Files.exists(logFile)) {
                Path expectedPath = projectDir.resolve(projectName != null ? projectName : "").resolve(breakingCommit + ".log");
                log.warn("Log file not found for record {}: expected at {}", record.descriptor(), expectedPath);
                if (verbose) {
                    System.out.println("  ⚠ Log file not found: " + expectedPath);
                }
                return;
            }

            if (verbose) {
                System.out.println("  Running breaking-classifier on: " + logFile);
            }

            // Run breaking-classifier
            BreakingReport report = runBreakingClassifier(logFile);

            if (report != null) {
                github.chains.breakingclassifier.FailureCategory category = report.failureCategory();
                System.out.println("  Category: " + category);
                if (verbose) {
                    System.out.println("  Original failure path: " + report.originalFailurePath());
                    System.out.println("  Errors found: " + report.errorsByFile().size());
                }
            } else {
                log.warn("Breaking-classifier returned null for record: {}", record.descriptor());
                if (verbose) {
                    System.out.println("  ⚠ Breaking-classifier returned null");
                }
            }

        } catch (Exception e) {
            log.error("Error extracting JARs or running classifier for record: {}", record.descriptor(), e);
            if (verbose) {
                System.out.println("  ✗ Error: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    /**
     * Extracts the previous version JAR from the pre Docker image's m2 folder.
     * Uses the pre Docker image (from preCommitReproductionCommand) to extract the JAR.
     *
     * @param dockerBuild the DockerBuild instance
     * @param preDockerImage the pre Docker image name
     * @param groupId the Maven group ID
     * @param artifactId the Maven artifact ID
     * @param previousVersion the previous version
     * @param outputDir the directory where JAR should be saved
     * @param verbose whether to show verbose output
     * @return the path to the extracted JAR, or null if not found
     */
    private static Path extractPreviousJarFromPreDockerImage(DockerBuild dockerBuild, String preDockerImage,
                                                              String groupId, String artifactId, String previousVersion,
                                                              Path outputDir, boolean verbose) {
        try {
            // Use the new simplified method to extract a single JAR from the pre Docker image
            Path previousJar = dockerBuild.extractJarFromImage(
                preDockerImage,
                groupId,
                artifactId,
                previousVersion,
                outputDir
            );

            if (previousJar != null && Files.exists(previousJar)) {
                log.info("Successfully extracted previous JAR from pre Docker image: {}", previousJar);
                return previousJar;
            }

            log.warn("Previous JAR not found in pre Docker image: {}", preDockerImage);
            return null;

        } catch (Exception e) {
            log.warn("Failed to extract previous JAR from pre Docker image {}: {}", preDockerImage, e.getMessage());
            if (verbose) {
                System.out.println("    ⚠ Could not extract previous JAR from pre Docker image: " + e.getMessage());
            }
            return null;
        }
    }

    /**
     * Finds and copies the new version JAR from the Maven local repository (m2 folder).
     * Maven stores dependencies in: .m2/repository/{groupId}/{artifactId}/{version}/{artifactId}-{version}.jar
     *
     * @param m2Dir the m2 directory path
     * @param groupId the Maven group ID
     * @param artifactId the Maven artifact ID
     * @param newVersion the new version
     * @param outputDir the directory where JAR should be copied
     * @return the path to the copied JAR, or null if not found
     */
    private static Path findAndCopyNewJarFromM2(Path m2Dir, String groupId, String artifactId, 
                                                 String newVersion, Path outputDir) {
        if (!Files.exists(m2Dir) || !Files.isDirectory(m2Dir)) {
            log.warn("M2 directory does not exist: {}", m2Dir);
            return null;
        }

        // Build Maven repository path: repository/{groupId}/{artifactId}/{version}/{artifactId}-{version}.jar
        String groupPath = groupId.replace(".", "/");
        Path repositoryDir = m2Dir.resolve("repository");
        
        if (!Files.exists(repositoryDir)) {
            // Try alternative locations
            repositoryDir = m2Dir.resolve(".m2").resolve("repository");
            if (!Files.exists(repositoryDir)) {
                repositoryDir = m2Dir;
            }
        }

        // Find and copy new version JAR
        if (newVersion != null && !newVersion.trim().isEmpty()) {
            Path newJarSource = buildMavenJarPath(repositoryDir, groupPath, artifactId, newVersion);
            if (Files.exists(newJarSource)) {
                Path newJarDest = outputDir.resolve("%s-%s.jar".formatted(artifactId, newVersion));
                try {
                    Files.copy(newJarSource, newJarDest, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    log.info("Copied new JAR: {} -> {}", newJarSource, newJarDest);
                    return newJarDest;
                } catch (IOException e) {
                    log.warn("Failed to copy new JAR from {} to {}", newJarSource, newJarDest, e);
                }
            } else {
                log.warn("New version JAR not found at: {}", newJarSource);
            }
        }

        return null;
    }

    /**
     * Deletes a directory and all its contents recursively.
     *
     * @param directory the directory to delete
     * @throws IOException if deletion fails
     */
    private static void deleteDirectory(Path directory) throws IOException {
        if (!Files.exists(directory)) {
            return;
        }
        
        if (Files.isDirectory(directory)) {
            try (java.nio.file.DirectoryStream<Path> stream = Files.newDirectoryStream(directory)) {
                for (Path entry : stream) {
                    deleteDirectory(entry);
                }
            }
        }
        
        Files.delete(directory);
    }

    /**
     * Builds the Maven JAR path based on groupId, artifactId, and version.
     * Structure: {repositoryDir}/{groupId}/{artifactId}/{version}/{artifactId}-{version}.jar
     *
     * @param repositoryDir the Maven repository directory
     * @param groupPath the group ID with dots replaced by slashes (e.g., "com/example")
     * @param artifactId the artifact ID
     * @param version the version
     * @return the path to the JAR file
     */
    private static Path buildMavenJarPath(Path repositoryDir, String groupPath, String artifactId, String version) {
        String jarFileName = "%s-%s.jar".formatted(artifactId, version);
        return repositoryDir.resolve(groupPath).resolve(artifactId).resolve(version).resolve(jarFileName);
    }

    /**
     * Finds the log file in the project directory.
     * The log file is located at: project/{project}/{breakingCommit}.log or similar patterns.
     *
     * @param projectDir the project directory (output/{breakingCommit}/project)
     * @param projectName the project name (used to find project/{project}/ subdirectory)
     * @param breakingCommit the breaking commit hash
     * @return the path to the log file, or null if not found
     */
    private static Path findLogFile(Path projectDir, String projectName, String breakingCommit) {
        if (!Files.exists(projectDir) || !Files.isDirectory(projectDir)) {
            return null;
        }

        // First, try to find the project subdirectory: project/{project}/
        Path projectSubDir = null;
        if (projectName != null && !projectName.trim().isEmpty()) {
            projectSubDir = projectDir.resolve(projectName);
            if (!Files.exists(projectSubDir) || !Files.isDirectory(projectSubDir)) {
                // Try to find any subdirectory that might be the project folder
                try {
                    projectSubDir = Files.list(projectDir)
                        .filter(path -> Files.isDirectory(path))
                        .findFirst()
                        .orElse(null);
                } catch (IOException e) {
                    log.warn("Error searching for project subdirectory in {}", projectDir, e);
                    projectSubDir = null;
                }
            }
        } else {
            // If project name is not available, try to find any subdirectory
            try {
                projectSubDir = Files.list(projectDir)
                    .filter(path -> Files.isDirectory(path))
                    .findFirst()
                    .orElse(null);
            } catch (IOException e) {
                log.warn("Error searching for project subdirectory in {}", projectDir, e);
            }
        }

        // If we found a project subdirectory, search for log files inside it
        if (projectSubDir != null && Files.exists(projectSubDir)) {
            // Try exact match first: project/{project}/{breakingCommit}.log
            Path exactMatch = projectSubDir.resolve(breakingCommit + ".log");
            if (Files.exists(exactMatch)) {
                return exactMatch;
            }

            // Try other common log file names in project/{project}/
            String[] logPatterns = {
                breakingCommit + ".log",
                "mavenLog.log",
                "output.log",
                "build.log",
                "maven.log"
            };

            for (String pattern : logPatterns) {
                Path logPath = projectSubDir.resolve(pattern);
                if (Files.exists(logPath)) {
                    return logPath;
                }
            }

            // Search for any .log file in project/{project}/
            try {
                return Files.list(projectSubDir)
                    .filter(path -> path.toString().endsWith(".log"))
                    .findFirst()
                    .orElse(null);
            } catch (IOException e) {
                log.warn("Error searching for log files in {}", projectSubDir, e);
            }
        }

        // Fallback: search directly in project directory (for backward compatibility)
        Path exactMatch = projectDir.resolve(breakingCommit + ".log");
        if (Files.exists(exactMatch)) {
            return exactMatch;
        }

        // Search for any .log file in the project directory
        try {
            return Files.list(projectDir)
                .filter(path -> path.toString().endsWith(".log"))
                .findFirst()
                .orElse(null);
        } catch (IOException e) {
            log.warn("Error searching for log files in {}", projectDir, e);
            return null;
        }
    }

    /**
     * Runs breaking-classifier on a log file and returns the report.
     *
     * @param logFile the path to the log file
     * @return the BreakingReport, or null if classification failed
     */
    private static BreakingReport runBreakingClassifier(Path logFile) {
        try {
            MavenErrorExtractor extractor = new MavenErrorExtractor();
            List<github.chains.breakingclassifier.BreakingError> errors = extractor.extract(logFile);
            BreakingReport report = ErrorReportAggregator.aggregate(logFile, errors);
            return report;
        } catch (Exception e) {
            log.error("Error running breaking-classifier on {}", logFile, e);
            return null;
        }
    }

    /**
     * Extracts Docker image name from a reproduction command.
     *
     * @param reproductionCommand the reproduction command string
     * @param dockerBuild the DockerBuild instance to use
     * @return the Docker image name, or null if not found
     */
    private static String extractDockerImageFromCommand(String reproductionCommand, DockerBuild dockerBuild) {
        if (reproductionCommand == null || reproductionCommand.trim().isEmpty()) {
            return null;
        }

        // Use DockerBuild's method to extract the image
        String image = dockerBuild.extractDockerImageFromCommand(reproductionCommand);
        
        // Return the image as-is (keeping "-breaking" suffix if present)
        return image;
    }

    /**
     * Prints usage information.
     */
    private static void printUsage() {
        System.out.println("Usage: Main [options]");
        System.out.println();
        System.out.println("Options:");
        System.out.println("  -i, --input DIR     Input directory containing BreakingUpdateRecord JSON files");
        System.out.println("                      (default: " + DEFAULT_INPUT_DIR + ")");
        System.out.println("  -o, --output DIR    Output directory (default: " + DEFAULT_OUTPUT_DIR + ")");
        System.out.println("  -c, --category CAT  Filter by failure category (see categories below)");
        System.out.println("  -f, --file NAME     Process only the specified JSON file (name without .json extension)");
        System.out.println("  -e, --extract       Extract projects from Docker images (default: " + DEFAULT_EXTRACT_PROJECTS + ")");
        System.out.println("  --no-extract        Do not extract projects from Docker images");
        System.out.println("  -k, --classify     Extract JARs and run breaking-classifier (default: " + DEFAULT_EXTRACT_JARS_AND_CLASSIFY + ")");
        System.out.println("  --no-classify       Do not extract JARs or run breaking-classifier");
        System.out.println("  --clean            Remove existing {breakingCommit} folders before extraction (default: " + DEFAULT_CLEAN_EXISTING + ")");
        System.out.println("  --no-clean         Keep existing {breakingCommit} folders (skip if exists)");
        System.out.println("  -v, --verbose       Show detailed information for each record");
        System.out.println("  -h, --help          Show this help message");
        System.out.println();
        System.out.println("Available Failure Categories:");
        for (FailureCategory category : FailureCategory.values()) {
            System.out.println("  - " + category.name());
        }
        System.out.println();
        System.out.println("Examples:");
        System.out.println("  Main -i ./breaking-updates");
        System.out.println("  Main --input ./my-data --category COMPILATION_FAILURE --verbose");
        System.out.println("  Main ./breaking-updates -c TEST_FAILURE --extract  # Extract projects");
        System.out.println("  Main -i ./data -o ./output --extract --verbose  # Extract with verbose output");
        System.out.println("  Main -i ./data --file my-project  # Process only my-project.json");
        System.out.println("  Main -i ./data --file my-project --extract  # Process and extract single file");
        System.out.println("  Main -i ./data --extract --classify  # Extract projects, JARs, and run classifier");
        System.out.println("  Main -i ./data --extract --clean  # Extract and remove existing folders first");
    }
}

