package com.example.core.service;

import com.example.core.model.BreakingUpdateRecord;
import com.example.core.model.UpdatedDependency;
import com.example.core.pipeline.ProjectLogLocator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.kth.DockerBuild;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Service class responsible for extracting JAR files from Docker images
 * and Maven repositories.
 */
public class JarExtractionService {

    private static final Logger log = LoggerFactory.getLogger(JarExtractionService.class);

    private final boolean verbose;
    private final DockerBuild dockerBuild;
    private final ClassificationService classificationService;

    public JarExtractionService(boolean verbose, DockerBuild dockerBuild) {
        this.verbose = verbose;
        this.dockerBuild = dockerBuild;
        this.classificationService = new ClassificationService(verbose);
    }

    /**
     * Extracts JARs from Docker image and runs breaking-classifier on the project.
     *
     * @param record        the breaking update record
     * @param dockerImage   the Docker image name
     * @param extractedPath the path where the project was extracted
     * @param breakingCommit the breaking commit hash
     */
    public ClassificationOutcome extractJarsAndRunClassifier(
            BreakingUpdateRecord record,
            String dockerImage,
            Path extractedPath,
            String breakingCommit) {

        try {
            UpdatedDependency updatedDependency = record.updatedDependency();
            if (updatedDependency == null) {
                log.warn("No updated dependency information for record: {}", record.descriptor());
                if (verbose) {
                    System.out.println("  ⚠ Skipping JAR extraction: No dependency information");
                }
                return null;
            }

            // Extract previous JAR from pre Docker image
            Path previousJarPath = null;
            if (updatedDependency.previousVersion() != null && !updatedDependency.previousVersion().trim().isEmpty()) {
                String preDockerImage = dockerBuild.extractDockerImageFromCommand(record.preCommitReproductionCommand());
                if (preDockerImage != null && !preDockerImage.trim().isEmpty()) {
                    if (verbose) {
                        System.out.println("  Extracting previous JAR from pre Docker image: " + preDockerImage);
                    }
                    previousJarPath = extractPreviousJarFromPreDockerImage(
                            preDockerImage,
                            updatedDependency.dependencyGroupId(),
                            updatedDependency.dependencyArtifactId(),
                            updatedDependency.previousVersion(),
                            extractedPath
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
            Map<String, Path> jarPaths = new HashMap<>();
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

            // Find log file and run classifier
            Path projectDir = extractedPath;
            String projectName = record.project();
            Path logFile = ProjectLogLocator.findLogFile(projectDir, projectName, breakingCommit);

            if (logFile == null || !Files.exists(logFile)) {
                Path expectedPath = projectDir.resolve(projectName != null ? projectName : "").resolve(breakingCommit + ".log");
                log.warn("Log file not found for record {}: expected at {}", record.descriptor(), expectedPath);
                if (verbose) {
                    System.out.println("  ⚠ Log file not found: " + expectedPath);
                }
                return null;
            }

            // Run breaking-classifier (CLI) and persist report next to the commit folder
            return classificationService.runClassifier(logFile, extractedPath);

        } catch (Exception e) {
            log.error("Error extracting JARs or running classifier for record: {}", record.descriptor(), e);
            if (verbose) {
                System.out.println("  ✗ Error: " + e.getMessage());
                e.printStackTrace();
            }
            return null;
        }
    }

    /**
     * Extracts the previous version JAR from the pre Docker image's m2 folder.
     *
     * @param preDockerImage  the pre Docker image name
     * @param groupId         the Maven group ID
     * @param artifactId      the Maven artifact ID
     * @param previousVersion the previous version
     * @param outputDir       the directory where JAR should be saved
     * @return the path to the extracted JAR, or null if not found
     */
    private Path extractPreviousJarFromPreDockerImage(
            String preDockerImage,
            String groupId,
            String artifactId,
            String previousVersion,
            Path outputDir) {

        try {
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
     * @param m2Dir      the m2 directory path
     * @param groupId     the Maven group ID
     * @param artifactId the Maven artifact ID
     * @param newVersion the new version
     * @param outputDir   the directory where JAR should be copied
     * @return the path to the copied JAR, or null if not found
     */
    private Path findAndCopyNewJarFromM2(
            Path m2Dir,
            String groupId,
            String artifactId,
            String newVersion,
            Path outputDir) {

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
     * Builds the Maven JAR path based on groupId, artifactId, and version.
     * Structure: {repositoryDir}/{groupId}/{artifactId}/{version}/{artifactId}-{version}.jar
     *
     * @param repositoryDir the Maven repository directory
     * @param groupPath     the group ID with dots replaced by slashes (e.g., "com/example")
     * @param artifactId    the artifact ID
     * @param version       the version
     * @return the path to the JAR file
     */
    private Path buildMavenJarPath(Path repositoryDir, String groupPath, String artifactId, String version) {
        String jarFileName = "%s-%s.jar".formatted(artifactId, version);
        return repositoryDir.resolve(groupPath).resolve(artifactId).resolve(version).resolve(jarFileName);
    }
}

