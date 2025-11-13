package com.example.core.bump;

import com.example.core.model.BreakingUpdateRecord;
import com.example.core.model.UpdatedDependency;
import com.example.core.bump.model.VersionCombination;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.kth.DockerBuild;
import java.util.HashSet;
import java.util.Set;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * Service for extracting JAR dependencies from Docker images based on version combinations.
 * Organizes extracted JARs by combination (groupId:artifactId:previousVersion-newVersion).
 */
public class DependencyExtractor {
    
    private static final Logger log = LoggerFactory.getLogger(DependencyExtractor.class);
    private final DockerBuild dockerBuild;
    private final Path outputBaseDir;
    private final boolean verbose;
    private CombinationUpdateCallback updateCallback;
    
    /**
     * Callback interface for updating the report after each combination is processed.
     */
    public interface CombinationUpdateCallback {
        void onCombinationProcessed(VersionCombination combination, Integer apiDiffLines);
    }
    
    public DependencyExtractor(Path outputBaseDir, boolean verbose) {
        this.dockerBuild = new DockerBuild(false);
        this.outputBaseDir = outputBaseDir;
        this.verbose = verbose;
    }
    
    /**
     * Sets a callback to be invoked after each combination is processed.
     * 
     * @param callback the callback to invoke
     */
    public void setCombinationUpdateCallback(CombinationUpdateCallback callback) {
        this.updateCallback = callback;
    }
    
    /**
     * Extracts JAR dependencies for unique version combinations.
     * Uses the first available record for each combination to extract dependencies.
     * Also runs japicmp to generate API diff files and counts the lines.
     *
     * @param combinations list of version combinations
     * @param recordsByCombination map of combination key to list of records
     * @return extraction summary with API diff line counts
     */
    public ExtractionSummary extractDependencies(
            List<VersionCombination> combinations,
            Map<String, List<BreakingUpdateRecord>> recordsByCombination) {
        
        log.info("Extracting dependencies for {} unique combinations", combinations.size());
        
        int extracted = 0;
        int skipped = 0;
        int failed = 0;
        Map<String, Integer> apiDiffLinesMap = new HashMap<>();
        
        for (VersionCombination combination : combinations) {
            // Create the same key format as VersionCombinationAnalyzer
            String combinationKey = combination.dependencyGroupId() + ":" + 
                                   combination.dependencyArtifactId() + ":" + 
                                   combination.previousVersion() + " -> " + combination.newVersion();
            List<BreakingUpdateRecord> records = recordsByCombination.get(combinationKey);
            
            if (records == null || records.isEmpty()) {
                log.warn("No records found for combination: {}", combinationKey);
                failed++;
                continue;
            }
            
            // Use the first record for this combination
            BreakingUpdateRecord record = records.get(0);
            UpdatedDependency dependency = record.updatedDependency();
            
            if (dependency == null) {
                log.warn("No dependency information for combination: {}", combinationKey);
                failed++;
                continue;
            }
            
            // Extract Docker images for this combination
            String previousImage = extractDockerImageFromCommand(record.preCommitReproductionCommand());
            String newImage = extractDockerImageFromCommand(record.breakingUpdateReproductionCommand());
            
            boolean extractedPrevious = extractJarVersion(
                    combination, 
                    record, 
                    dependency.previousVersion(), 
                    record.preCommitReproductionCommand(),
                    "previous"
            );
            
            boolean extractedNew = extractJarVersion(
                    combination, 
                    record, 
                    dependency.newVersion(), 
                    record.breakingUpdateReproductionCommand(),
                    "new"
            );
            
            boolean bothJarsAvailable = bothJarsExist(combination);
            
            if (extractedPrevious || extractedNew) {
                extracted++;
                if (verbose) {
                    System.out.println("  ✓ Extracted JARs for: " + combinationKey);
                }
            } else {
                // Check if both JARs already exist
                if (bothJarsAvailable) {
                    skipped++;
                    if (verbose) {
                        System.out.println("  ⊙ Skipped (already exist): " + combinationKey);
                    }
                } else {
                    failed++;
                    if (verbose) {
                        System.out.println("  ✗ Failed to extract: " + combinationKey);
                    }
                }
            }
            
            // Run japicmp if both JARs are available
            Integer diffLines = null;
            if (bothJarsAvailable) {
                try {
                    diffLines = runJapicmp(combination);
                    if (diffLines != null) {
                        apiDiffLinesMap.put(combinationKey, diffLines);
                        if (verbose) {
                            System.out.println("  ✓ Generated API diff with " + diffLines + " lines for: " + combinationKey);
                        }
                    }
                } catch (Exception e) {
                    log.warn("Failed to run japicmp for combination {}: {}", combinationKey, e.getMessage());
                }
            }
            
            // Delete Docker images immediately after processing this combination
            Set<String> imagesToDelete = new HashSet<>();
            if (previousImage != null && !previousImage.trim().isEmpty()) {
                imagesToDelete.add(previousImage);
            }
            if (newImage != null && !newImage.trim().isEmpty()) {
                imagesToDelete.add(newImage);
            }
            
            // Remove duplicates (in case previous and new are the same image)
            for (String imageId : imagesToDelete) {
                try {
                    DockerBuild.deleteImage(imageId);
                    if (verbose) {
                        System.out.println("  ✓ Deleted Docker image: " + imageId);
                    }
                    log.info("Deleted Docker image after processing combination: {}", imageId);
                } catch (Exception e) {
                    log.warn("Failed to delete Docker image {}: {}", imageId, e.getMessage());
                }
            }
            
            // Notify callback to update report with this combination's data
            if (updateCallback != null) {
                // Create updated combination with API diff lines
                VersionCombination updatedCombination = new VersionCombination(
                        combination.dependencyGroupId(),
                        combination.dependencyArtifactId(),
                        combination.previousVersion(),
                        combination.newVersion(),
                        combination.count(),
                        combination.projects(),
                        combination.breakingCommits(),
                        combination.failureCategories(),
                        diffLines
                );
                updateCallback.onCombinationProcessed(updatedCombination, diffLines);
            }
        }
        
        log.info("Extraction complete: {} extracted, {} skipped, {} failed", extracted, skipped, failed);
        
        return new ExtractionSummary(extracted, skipped, failed, apiDiffLinesMap);
    }
    
    /**
     * Extracts a JAR for a specific version from a Docker image.
     *
     * @param combination the version combination
     * @param record the breaking update record
     * @param version the version to extract
     * @param reproductionCommand the Docker reproduction command
     * @param versionType "previous" or "new"
     * @return true if extracted or already exists, false otherwise
     */
    private boolean extractJarVersion(
            VersionCombination combination,
            BreakingUpdateRecord record,
            String version,
            String reproductionCommand,
            String versionType) {
        
        if (version == null || version.trim().isEmpty()) {
            log.debug("Skipping {} version: version is null or empty", versionType);
            return false;
        }
        
        // Get combination directory
        Path combinationDir = getCombinationDir(combination);
        Path jarPath = combinationDir.resolve(combination.dependencyArtifactId() + "-" + version + ".jar");
        
        // Check if JAR already exists
        if (Files.exists(jarPath)) {
            log.debug("JAR already exists: {}", jarPath);
            return true;
        }
        
        // Extract Docker image from reproduction command
        String dockerImage = extractDockerImageFromCommand(reproductionCommand);
        if (dockerImage == null || dockerImage.trim().isEmpty()) {
            log.warn("Could not extract Docker image from reproduction command for {} version", versionType);
            return false;
        }
        
        // Create combination directory if it doesn't exist
        try {
            Files.createDirectories(combinationDir);
        } catch (IOException e) {
            log.error("Failed to create combination directory: {}", combinationDir, e);
            return false;
        }
        
        // Extract JAR from Docker image
        try {
            if (verbose) {
                System.out.println("    Extracting " + versionType + " version " + version + 
                        " from Docker image: " + dockerImage);
            }
            
            Path extractedJar = dockerBuild.extractJarFromImage(
                    dockerImage,
                    combination.dependencyGroupId(),
                    combination.dependencyArtifactId(),
                    version,
                    combinationDir
            );
            
            if (extractedJar != null && Files.exists(extractedJar)) {
                log.info("Successfully extracted {} JAR: {}", versionType, extractedJar);
                return true;
            } else {
                log.warn("Failed to extract {} JAR from Docker image: {}", versionType, dockerImage);
                return false;
            }
            
        } catch (Exception e) {
            log.error("Error extracting {} JAR from Docker image {}: {}", 
                    versionType, dockerImage, e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * Checks if both JARs (previous and new versions) exist for a combination.
     *
     * @param combination the version combination
     * @return true if both JARs exist, false otherwise
     */
    private boolean bothJarsExist(VersionCombination combination) {
        Path combinationDir = getCombinationDir(combination);
        Path previousJar = combinationDir.resolve(
                combination.dependencyArtifactId() + "-" + combination.previousVersion() + ".jar");
        Path newJar = combinationDir.resolve(
                combination.dependencyArtifactId() + "-" + combination.newVersion() + ".jar");
        
        return Files.exists(previousJar) && Files.exists(newJar);
    }
    
    /**
     * Gets the directory path for a version combination.
     * Creates a directory with a descriptive name for the combination.
     * Format: outputBaseDir/{groupId}_{artifactId}_{previousVersion}-{newVersion}
     *
     * @param combination the version combination
     * @return the combination directory path
     */
    private Path getCombinationDir(VersionCombination combination) {
        // Create a directory name: groupId_artifactId_previousVersion-newVersion
        // Replace dots and special characters with underscores for filesystem compatibility
        String groupId = combination.dependencyGroupId().replace(".", "_").replace("/", "_");
        String artifactId = combination.dependencyArtifactId();
        String versionRange = combination.previousVersion() + "-" + combination.newVersion();
        String dirName = String.format("%s_%s_%s", groupId, artifactId, versionRange);
        
        return outputBaseDir.resolve(dirName);
    }
    
    /**
     * Extracts Docker image name from a reproduction command.
     *
     * @param reproductionCommand the reproduction command string
     * @return the Docker image name, or null if not found
     */
    private String extractDockerImageFromCommand(String reproductionCommand) {
        if (reproductionCommand == null || reproductionCommand.trim().isEmpty()) {
            return null;
        }
        
        try {
            // Use DockerBuild's method to extract the image
            String image = dockerBuild.extractDockerImageFromCommand(reproductionCommand);
            return image;
        } catch (Exception e) {
            log.warn("Failed to extract Docker image from command: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * Runs japicmp to generate API diff between two JAR versions.
     * 
     * @param combination the version combination
     * @return the number of lines in the generated diff file, or null if generation failed
     */
    private Integer runJapicmp(VersionCombination combination) {
        Path combinationDir = getCombinationDir(combination);
        Path previousJar = combinationDir.resolve(
                combination.dependencyArtifactId() + "-" + combination.previousVersion() + ".jar");
        Path newJar = combinationDir.resolve(
                combination.dependencyArtifactId() + "-" + combination.newVersion() + ".jar");
        Path diffFile = combinationDir.resolve("api_diff.txt");
        
        // Check if diff file already exists
        if (Files.exists(diffFile)) {
            try {
                return countLines(diffFile);
            } catch (IOException e) {
                log.warn("Failed to count lines in existing diff file: {}", e.getMessage());
            }
        }
        
        // Check if both JARs exist
        if (!Files.exists(previousJar) || !Files.exists(newJar)) {
            log.warn("Cannot run japicmp: JARs not found. Previous: {}, New: {}", 
                    previousJar, newJar);
            return null;
        }
        
        try {
            // Create combination directory if it doesn't exist
            Files.createDirectories(combinationDir);
            
            // Build japicmp command as string
            String command = buildJapicmpCommand(previousJar, newJar, diffFile);
            
            if (command == null) {
                log.warn("Could not find japicmp command or JAR. Please ensure japicmp is in PATH or configure japicmp.jar path.");
                return null;
            }
            
            // Print command to console
            System.out.println("    Executing command: " + command);
            log.info("Executing japicmp command: {}", command);
            
            // Execute command using shell
            ProcessBuilder processBuilder;
            String os = System.getProperty("os.name").toLowerCase();
            if (os.contains("win")) {
                // Windows
                processBuilder = new ProcessBuilder("cmd.exe", "/c", command);
            } else {
                // Unix/Linux/Mac
                processBuilder = new ProcessBuilder("sh", "-c", command);
            }
            
            processBuilder.directory(combinationDir.toFile());
            processBuilder.redirectErrorStream(true);
            
            Process process = processBuilder.start();
            
            // Read output for logging
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (verbose) {
                        log.debug("japicmp output: {}", line);
                    }
                }
            }
            
            int exitCode = process.waitFor();
            
            if (exitCode != 0) {
                log.warn("japicmp exited with code {} for combination: {}", exitCode, 
                        combination.dependencyGroupId() + ":" + combination.dependencyArtifactId());
                return null;
            }
            
            // Check if diff file was created
            if (!Files.exists(diffFile)) {
                log.warn("japicmp did not create diff file: {}", diffFile);
                return null;
            }
            
            // Count lines in the generated file
            int lineCount = countLines(diffFile);
            log.info("Generated API diff with {} lines for combination: {}", lineCount,
                    combination.dependencyGroupId() + ":" + combination.dependencyArtifactId());
            
            return lineCount;
            
        } catch (IOException e) {
            log.error("Error running japicmp for combination {}:{}: {}", 
                    combination.dependencyGroupId(), combination.dependencyArtifactId(), 
                    e.getMessage(), e);
            return null;
        } catch (InterruptedException e) {
            log.error("japicmp process interrupted for combination {}:{}: {}", 
                    combination.dependencyGroupId(), combination.dependencyArtifactId(), 
                    e.getMessage(), e);
            Thread.currentThread().interrupt();
            return null;
        }
    }
    
    /**
     * Builds the japicmp command as a string.
     * 
     * @param previousJar the previous version JAR
     * @param newJar the new version JAR
     * @param diffFile the output diff file
     * @return the command string, or null if japicmp cannot be found
     */
    private String buildJapicmpCommand(Path previousJar, Path newJar, Path diffFile) {
        // Build command string: java -jar japicmp.jar --ignore-missing-classes -m -o <previous> -n <new> <output> > diffFile
        String japicmpJar = "/Users/frankreyesgarcia/Documents/WORK/PHD/Tools/japicmp/japicmp-0.24.2-jar-with-dependencies.jar";
        
        // Check if JAR exists
        Path jarPath = Paths.get(japicmpJar);
        if (!Files.exists(jarPath)) {
            log.error("japicmp JAR not found at: {}", japicmpJar);
            return null;
        }
        
        // Build command string without quotes and with output redirection
        String command = String.format(
            "java -jar %s --ignore-missing-classes -m -o %s -n %s > %s",
            jarPath.toAbsolutePath().toString(),
            previousJar.toAbsolutePath().toString(),
            newJar.toAbsolutePath().toString(),
            diffFile.toAbsolutePath().toString()
        );
        
        return command;
    }
    
    /**
     * Counts the number of lines in a file.
     * 
     * @param file the file to count lines in
     * @return the number of lines
     * @throws IOException if there's an error reading the file
     */
    private int countLines(Path file) throws IOException {
        int lineCount = 0;
        try (BufferedReader reader = Files.newBufferedReader(file)) {
            while (reader.readLine() != null) {
                lineCount++;
            }
        }
        return lineCount;
    }
    
    /**
     * Summary of dependency extraction results.
     */
    public static class ExtractionSummary {
        private final int extracted;
        private final int skipped;
        private final int failed;
        private final Map<String, Integer> apiDiffLines;
        
        public ExtractionSummary(int extracted, int skipped, int failed) {
            this(extracted, skipped, failed, new HashMap<>());
        }
        
        public ExtractionSummary(int extracted, int skipped, int failed, Map<String, Integer> apiDiffLines) {
            this.extracted = extracted;
            this.skipped = skipped;
            this.failed = failed;
            this.apiDiffLines = apiDiffLines != null ? apiDiffLines : new HashMap<>();
        }
        
        public int extracted() {
            return extracted;
        }
        
        public int skipped() {
            return skipped;
        }
        
        public int failed() {
            return failed;
        }
        
        public int total() {
            return extracted + skipped + failed;
        }
        
        public Map<String, Integer> apiDiffLines() {
            return apiDiffLines;
        }
    }
}

