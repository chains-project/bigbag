package com.example.core.service;

import com.example.core.config.EnvConfig;
import com.example.core.model.BreakingUpdateRecord;
import com.example.core.util.ProjectPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.kth.DockerBuild;
import se.kth.models.Attempt;
import se.kth.models.FailureCategory;
import se.kth.models.Result;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Service responsible for managing the sequential repair loop.
 * It orchestrates git branching, compilation, and error analysis for each
 * attempt.
 */
public class RepairLoopService {

    private static final Logger log = LoggerFactory.getLogger(RepairLoopService.class);

    private final GitWorkflowService gitWorkflowService;
    private final DockerBuild dockerBuild;
    private final EnvConfig envConfig;
    private final boolean verbose;

    public RepairLoopService(GitWorkflowService gitWorkflowService, DockerBuild dockerBuild, EnvConfig envConfig,
            boolean verbose) {
        this.gitWorkflowService = gitWorkflowService;
        this.dockerBuild = dockerBuild;
        this.envConfig = envConfig;
        this.verbose = verbose;
    }

    /**
     * Runs the repair loop for a specific project.
     *
     * @param extractedPath the root path where the project was extracted (contains
     *                      project dir and m2)
     * @param projectName   the name of the project
     * @param dockerImage   the docker image used for reproduction
     * @param record        the breaking update record
     */
    public List<Attempt> runRepairLoop(Path extractedPath, String projectName, String dockerImage,
            BreakingUpdateRecord record) {
        Path projectDir = ProjectPaths.resolveProjectDir(extractedPath, projectName);
        int maxAttempts = Integer.parseInt(envConfig.get("MAX_REPAIR_ATTEMPTS").orElse("3"));
        List<Attempt> attempts = new ArrayList<>();

        log.info("Starting repair loop for {} with {} attempts", projectName, maxAttempts);

        // 1. Ensure clean start: checkout initial branch (master)
        try {
            gitWorkflowService.checkout(projectDir, "master");
        } catch (Exception e) {
            log.warn("Could not checkout master, assuming we are already there or it's the first run.");
        }

        // 2. Clean up previous attempt branches if they exist
        for (int i = 1; i <= maxAttempts; i++) {
            gitWorkflowService.deleteBranch(projectDir, "repair/attempt-" + i);
        }

        // 3. Create initial branch for attempt 1 from master
        // The user requested: "first you create an initial branch and then before
        // starting attempt 1 you create the branch for that attempt"
        // But master IS the initial branch created by initAndCommit.
        // So we start Attempt 1 from master.

        String currentBranch = "master";
        Path previousLogFile = null; // Track log from previous attempt for analysis

        for (int i = 1; i <= maxAttempts; i++) {
            String attemptBranch = "repair/attempt-" + i;
            log.info("=== Starting Attempt {}/{} ===", i, maxAttempts);

            try {
                // Create branch for this attempt from the previous one (sequential)
                gitWorkflowService.createBranchFromBase(projectDir, attemptBranch, currentBranch);
                currentBranch = attemptBranch;

                // Create attempt directory for artifacts
                Path attemptDir = extractedPath.resolve("patches").resolve("patch_" + i);
                Files.createDirectories(attemptDir);

                // Compile and Test
                Path logFile = attemptDir.resolve("maven-log.txt");
                log.info("Compiling and testing attempt {}...", i);

                // We use reproduceWithMount to run the build inside the container with the
                // mounted project
                // This ensures the environment is exactly the same as the original failure
                // The projectDir is mounted, so changes in the git branch are visible inside
                Result result = dockerBuild.reproduceWithMount(
                        dockerImage,
                        record.failureCategory() != null ? FailureCategory.valueOf(record.failureCategory())
                                : FailureCategory.UNKNOWN_FAILURE,
                        projectDir, // Mount the project directory
                        logFile);

                if (verbose) {
                    System.out.println("Attempt " + i + " result: " + result.getAttempts().get(0).getFailureCategory());
                }

                if (!result.getAttempts().isEmpty()) {
                    Attempt attempt = result.getAttempts().get(0);
                    attempts.add(attempt);

                    if (attempt.getFailureCategory() == FailureCategory.BUILD_SUCCESS) {
                        log.info("Attempt {} successful! Stopping loop.", i);
                        break;
                    }
                }

                // Analyze logs (Placeholder for now)
                // In a real implementation, we would parse logFile here to find errors

                // Placeholder: Simulate a fix and commit
                // gitWorkflowService.commitAll(projectDir, "Applied fix for attempt " + i);

                previousLogFile = logFile;

            } catch (Exception e) {
                log.error("Failed during attempt {}", i, e);
                break; // Stop loop on critical error
            }
        }
        return attempts;
    }
}
