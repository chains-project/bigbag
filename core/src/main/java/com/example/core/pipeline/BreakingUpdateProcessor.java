package com.example.core.pipeline;

import com.example.core.model.BreakingUpdateRecord;
import com.example.core.util.ProjectPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.kth.DockerBuild;
import se.kth.models.Attempt;
import se.kth.models.FailureCategory;
import se.kth.models.Result;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Processes a single breaking update record.
 * Handles extraction, building, and result storage.
 */
public class BreakingUpdateProcessor {

    private static final Logger log = LoggerFactory.getLogger(BreakingUpdateProcessor.class);

    private final DockerBuild dockerBuild;
    private final PipelineConfig config;
    private final ResultManager resultManager;

    public BreakingUpdateProcessor(DockerBuild dockerBuild, PipelineConfig config, ResultManager resultManager) {
        this.dockerBuild = dockerBuild;
        this.config = config;
        this.resultManager = resultManager;
    }

    /**
     * Processes a breaking update record.
     *
     * @param record the breaking update record to process
     * @return true if processing was successful, false otherwise
     */
    public boolean process(BreakingUpdateRecord record) {
        String breakingCommit = record.breakingCommit();
        log.info("=== Processing breaking update: {} ===", breakingCommit);

        try {
            // Extract Docker image from command
            String dockerImage = dockerBuild.extractDockerImageFromCommand(
                    record.breakingUpdateReproductionCommand());
            
            if (dockerImage == null) {
                log.error("Could not extract Docker image from command: {}", 
                        record.breakingUpdateReproductionCommand());
                return false;
            }

            log.info("Using Docker image: {}", dockerImage);

            // Get normalized project path in container
            String containerProjectPath = FailureCategoryUtils.normalizeContainerProjectPath(record.project());

            // Extract project and m2 from Docker image
            Path extractedDir = dockerBuild.extractProjectAndM2FromImage(
                    dockerImage,
                    containerProjectPath,
                    config.getProjectsPath(),
                    breakingCommit,
                    config.isForceReextraction()
            );

            Path projectDir = ProjectPaths.resolveProjectDir(extractedDir, record.project());
            if (!Files.exists(projectDir)) {
                log.error("Project directory not found after extraction: {}", projectDir);
                return false;
            }

            log.info("Project extracted to: {}", extractedDir);

            // Build the project
            Path buildLogFile = extractedDir.resolve("build.log");
            FailureCategory failureCategory = FailureCategoryUtils.parseFailureCategory(record.failureCategory());

            Result result = dockerBuild.reproduceWithMount(
                    dockerImage,
                    failureCategory,
                    projectDir,
                    buildLogFile,
                    containerProjectPath
            );

            // Store result
            resultManager.addResult(breakingCommit, result);
            
            // Save results after each successful processing
            resultManager.saveResults();

            boolean success = result.getAttempts().stream().anyMatch(Attempt::isSuccessful);
            log.info("Completed processing breaking update: {} - Success: {}", breakingCommit, success);
            
            return true;

        } catch (Exception e) {
            log.error("Error processing breaking update: {}", breakingCommit, e);
            return false;
        }
    }
}

