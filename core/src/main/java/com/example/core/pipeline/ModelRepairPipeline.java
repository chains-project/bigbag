package com.example.core.pipeline;

import com.example.core.config.EnvConfig;
import com.example.core.model.BreakingUpdateRecord;
import com.example.core.model.ClassificationSummary;
import com.example.core.prompt.PromptGenerationService;
import com.example.core.service.ChangeImpactReportService;
import com.example.core.service.GitWorkflowService;
import com.example.core.service.TransformedFileBuildService;
import com.example.core.util.ProjectPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.kth.DockerBuild;
import se.kth.models.Attempt;
import se.kth.models.FailureCategory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Model-based repair pipeline implementation.
 * This pipeline uses language models to generate prompts and transformed files for repair attempts.
 */
public class ModelRepairPipeline implements RepairPipeline {

    private static final Logger log = LoggerFactory.getLogger(ModelRepairPipeline.class);

    private final GitWorkflowService gitWorkflowService;
    private final DockerBuild dockerBuild;
    private final EnvConfig envConfig;
    private final boolean verbose;
    private final PromptGenerationService promptGenerationService;

    public ModelRepairPipeline(GitWorkflowService gitWorkflowService, DockerBuild dockerBuild, EnvConfig envConfig,
            boolean verbose) {
        this.gitWorkflowService = gitWorkflowService;
        this.dockerBuild = dockerBuild;
        this.envConfig = envConfig;
        this.verbose = verbose;
        this.promptGenerationService = new PromptGenerationService(envConfig);
    }

    @Override
    public List<Attempt> runRepairLoop(Path extractedPath, String projectName, String dockerImage,
            BreakingUpdateRecord record, Path commitReportDir, ClassificationSummary summary, Path outputBaseDir,
            Path initialLogFile) {
        log.info("Starting model-based repair loop for {} (commit: {})", projectName, record.breakingCommit());
        
        List<Attempt> attempts = new ArrayList<>();
        Path projectDir = ProjectPaths.resolveProjectDir(extractedPath, projectName);

        if (!Files.exists(projectDir)) {
            log.error("Project directory not found: {}", projectDir);
            return attempts;
        }

        // Initialize Git repository if not already initialized
        try {
            gitWorkflowService.initAndCommit(projectDir, "Initial commit - extracted project");
        } catch (Exception e) {
            log.warn("Git repository initialization failed or already exists: {}", e.getMessage());
        }

        // Create repair branch based on failure category
        String repairBranchName = "repair/"
                + (record.failureCategory() != null ? record.failureCategory().toLowerCase() : "unknown");
        try {
            gitWorkflowService.createAndCheckoutBranch(projectDir, repairBranchName);
            log.info("Created and checked out repair branch: {}", repairBranchName);
        } catch (Exception e) {
            log.error("Failed to create/checkout repair branch {}: {}", repairBranchName, e.getMessage(), e);
            return attempts;
        }

        // Get max attempts from environment or use default
        int maxAttempts = envConfig.get("MAX_REPAIR_ATTEMPTS")
                .map(Integer::parseInt)
                .orElse(3);

        log.info("Repair loop will run up to {} attempts", maxAttempts);

        TransformedFileBuildService transformedBuildService = new TransformedFileBuildService(dockerBuild, verbose);
        ChangeImpactReportService changeImpactService = new ChangeImpactReportService(verbose, envConfig);
        
        Path previousAttemptLogFile = initialLogFile; // For attempt 1, use initial log
        String previousBranch = repairBranchName; // Start from repair branch

        // Process each attempt
        for (int attemptNumber = 1; attemptNumber <= maxAttempts; attemptNumber++) {
            log.info("=== Processing attempt {} of {} ===", attemptNumber, maxAttempts);

            // Create attempt-specific directory structure
            Path attemptDir = commitReportDir.resolve("attempt_" + attemptNumber);
            try {
                Files.createDirectories(attemptDir);
                // Don't create transformed directory here - it will be created when needed in generateFilePrompts
                Path attemptPromptsDir = attemptDir.resolve("prompts");
                Files.createDirectories(attemptPromptsDir);
            } catch (IOException e) {
                log.error("Failed to create attempt directories: {}", e.getMessage(), e);
                break;
            }
            
            Path attemptTransformedDir = attemptDir.resolve("transformed");

            // Create Git branch for this attempt
            String branchName = "attempt_" + attemptNumber;
            try {
                if (attemptNumber == 1) {
                    gitWorkflowService.createBranchFromBase(projectDir, branchName, previousBranch);
                } else {
                    gitWorkflowService.createBranchFromBase(projectDir, branchName, previousBranch);
                }
                gitWorkflowService.checkout(projectDir, branchName);
                log.info("Created and checked out branch: {}", branchName);
            } catch (Exception e) {
                log.error("Failed to create/checkout branch {}: {}", branchName, e.getMessage(), e);
                break;
            }

            // STEP 1: Generate change-impact and prompts from previous attempt's log
            // This must happen BEFORE building, so we can use the change-impact in the prompts
            if (previousAttemptLogFile != null && Files.exists(previousAttemptLogFile)) {
                try {
                    log.info("[Attempt {}] Step 1: Analyzing previous attempt's log...", attemptNumber);
                    // Analyze previous attempt's log to get errors
                    Path previousClassifierReport = attemptDir.resolve("input_breaking-classifier-report.json");
                    github.chains.breakingclassifier.BreakingClassifierApp classifierApp = 
                            new github.chains.breakingclassifier.BreakingClassifierApp();
                    github.chains.breakingclassifier.BreakingReport breakingReport = 
                            classifierApp.analyzeLog(previousAttemptLogFile, previousClassifierReport);

                    if (breakingReport != null && !breakingReport.errorsByFile().isEmpty()) {
                        log.info("[Attempt {}] Step 1.1: Generating change-impact report...", attemptNumber);
                        // Generate change-impact report from previous attempt's log
                        // This includes the full analysis with ChangeImpactReport for each error
                        List<ChangeImpactReportService.FileImpact> fileImpacts = 
                                generateChangeImpactFromLog(record, summary, outputBaseDir, attemptDir, 
                                        previousClassifierReport, attemptNumber);
                        log.info("[Attempt {}] Step 1.1: Change-impact report generated. Found {} files with errors.", 
                                attemptNumber, fileImpacts.size());

                        if (!fileImpacts.isEmpty()) {
                            log.info("[Attempt {}] Step 1.2: Generating prompts and transformed files...", attemptNumber);
                            // Generate prompts for this attempt using change-impact data
                            promptGenerationService.generateFilePrompts(
                                    record,
                                    summary,
                                    attemptDir, // Use attempt-specific directory
                                    outputBaseDir,
                                    fileImpacts
                            );

                            log.info("[Attempt {}] Step 1: Completed - Generated prompts and transformed files for {} files", 
                                    attemptNumber, fileImpacts.size());
                        } else {
                            log.warn("[Attempt {}] Step 1: No file impacts generated from change-impact analysis", attemptNumber);
                        }
                    } else {
                        log.info("[Attempt {}] Step 1: No errors found in previous attempt's log", attemptNumber);
                    }
                } catch (com.example.core.prompt.TransformationFailureException e) {
                    // Handle transformation failure: create an attempt with TRANSFORMATION_FAILURE
                    log.error("Spoon transformation failed for attempt {}: {} - {}", 
                            attemptNumber, e.getErrorType(), e.getMessage());
                    
                    Attempt transformationFailureAttempt = new Attempt(
                            attemptNumber,
                            FailureCategory.TRANSFORMATION_FAILURE,
                            attemptDir.toString(),
                            false
                    );
                    attempts.add(transformationFailureAttempt);
                    
                    log.info("Recorded TRANSFORMATION_FAILURE for attempt {}. Stopping repair loop.", attemptNumber);
                    break; // Stop the repair loop on transformation failure
                } catch (Exception e) {
                    log.error("Failed to generate change-impact and prompts for attempt {}: {}", 
                            attemptNumber, e.getMessage(), e);
                    // Continue anyway - maybe transformed files already exist
                }
            } else if (attemptNumber == 1 && previousAttemptLogFile == null) {
                log.warn("No initial log file provided for attempt 1. Skipping repair loop.");
                break;
            }

            // STEP 2: Apply transformed files, commit, and build
            log.info("[Attempt {}] Step 2: Applying transformed files and building project...", attemptNumber);
            try {
                if (!Files.exists(attemptTransformedDir) || 
                    !Files.list(attemptTransformedDir).findAny().isPresent()) {
                    log.warn("[Attempt {}] Step 2: No transformed files found. Stopping repair loop.", attemptNumber);
                    break;
                }
            } catch (IOException e) {
                log.error("[Attempt {}] Step 2: Failed to check transformed files directory: {}", attemptNumber, e.getMessage(), e);
                break;
            }

            // Apply transformed files
            TransformedFileBuildService.BuildResult buildResult = transformedBuildService.replaceAndBuild(
                    attemptTransformedDir,
                    projectDir,
                    dockerImage,
                    record,
                    attemptDir, // Use attempt-specific directory for output
                    attemptNumber
            );

            if (buildResult == null) {
                log.warn("Build result is null for attempt {}. Stopping repair loop.", attemptNumber);
                break;
            }

            // Commit changes to Git before proceeding
            try {
                gitWorkflowService.commitAll(projectDir, 
                        String.format("Attempt %d: Applied transformed files", attemptNumber));
                log.info("Committed changes for attempt {}", attemptNumber);
            } catch (Exception e) {
                log.warn("Failed to commit changes for attempt {}: {}", attemptNumber, e.getMessage());
            }

            Attempt attempt = buildResult.getAttempt();
            attempts.add(attempt);

            log.info("[Attempt {}] Step 2: Build completed - Category: {}, Success: {}", 
                    attemptNumber, buildResult.getCategory(), buildResult.isSuccess());

            // STEP 3: Generate change-impact report for this attempt
            log.info("[Attempt {}] Step 3: Generating change-impact report for this attempt...", attemptNumber);
            changeImpactService.generateChangeImpactForAttempt(
                    record, summary, outputBaseDir, attemptDir, attemptNumber);
            log.info("[Attempt {}] Step 3: Change-impact report generated", attemptNumber);

            // If build succeeded, stop the loop
            if (buildResult.isSuccess() || buildResult.getCategory() == FailureCategory.BUILD_SUCCESS) {
                log.info("Build succeeded in attempt {}. Stopping repair loop.", attemptNumber);
                break;
            }

            // If this is NO_DIFF, stop
            Path logFile = buildResult.getLogFile();
            if (buildResult.getCategory() == FailureCategory.NO_DIFF ||
                (buildResult.getCategory() == FailureCategory.UNKNOWN_FAILURE &&
                        (!Files.exists(logFile) || logFile.toFile().length() == 0))) {
                log.info("No differences found in transformed files (NO_DIFF). Stopping repair loop.");
                break;
            }

            // Update for next attempt
            previousAttemptLogFile = logFile; // Use this attempt's log for next attempt
            previousBranch = branchName; // Use this attempt's branch as base for next attempt

            log.info("Attempt {} failed. Proceeding to attempt {}...", attemptNumber, attemptNumber + 1);
        }

        log.info("Repair loop completed for {} (commit: {}). Total attempts: {}", 
                projectName, record.breakingCommit(), attempts.size());
        return attempts;
    }

    /**
     * Generates change-impact report from a breaking-classifier report.
     * This includes the full analysis with ChangeImpactReport for each error.
     * 
     * @param record the breaking update record
     * @param summary the classification summary
     * @param outputBaseDir the output base directory
     * @param attemptDir the attempt directory
     * @param classifierReportPath the breaking-classifier report path
     * @param attemptNumber the attempt number (for logging)
     * @return list of FileImpact with change-impact data
     */
    private List<ChangeImpactReportService.FileImpact> generateChangeImpactFromLog(
            BreakingUpdateRecord record,
            ClassificationSummary summary,
            Path outputBaseDir,
            Path attemptDir,
            Path classifierReportPath,
            int attemptNumber) {
        log.info("Generating change-impact from log for attempt {} (input)", attemptNumber);
        
        ChangeImpactReportService changeImpactService = new ChangeImpactReportService(verbose, envConfig);
        
        // Generate change-impact from the breaking-classifier report
        // This will include the full analysis with ChangeImpactReport for each error
        Path inputChangeImpactPath = attemptDir.resolve("input_change-impact.json");
        
        return changeImpactService.generateChangeImpactFromClassifierReport(
                record, 
                summary, 
                outputBaseDir, 
                classifierReportPath,
                inputChangeImpactPath // Write to input_change-impact.json for reference
        );
    }
}

