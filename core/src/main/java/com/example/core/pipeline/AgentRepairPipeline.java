package com.example.core.pipeline;

import com.example.core.agent.AgentFactory;
import com.example.core.agent.BaseAgent;
import com.example.core.agent.model.AgentExecutionRequest;
import com.example.core.agent.model.AgentExecutionResult;
import com.example.core.config.EnvConfig;
import com.example.core.model.BreakingUpdateRecord;
import com.example.core.model.ClassificationSummary;
import com.example.core.service.ChangeImpactReportService;
import com.example.core.service.GitWorkflowService;
import com.example.core.service.ProcessIdService;
import com.example.core.util.FileSystemUtils;
import com.example.core.util.ProjectPaths;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.kth.DockerBuild;
import se.kth.models.Attempt;
import se.kth.models.FailureCategory;

import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Agent-based repair pipeline implementation.
 * 
 * This pipeline is now generic and scalable, using the Strategy/Factory pattern
 * to support multiple agent types (Spoon, OpenRewrite, etc.) without code changes.
 * 
 * The pipeline:
 * 1. Uses AgentFactory to dynamically select the appropriate agent based on environment variables
 * 2. Delegates all agent-specific logic (mounts, commands, setup) to the agent instance
 * 3. Remains agnostic to the specific agent implementation
 */
public class AgentRepairPipeline implements RepairPipeline {

    private static final Logger log = LoggerFactory.getLogger(AgentRepairPipeline.class);

    private final DockerBuild dockerBuild;
    private final EnvConfig envConfig;
    private final boolean verbose;
    private final ChangeImpactReportService changeImpactService;
    private final GitWorkflowService gitWorkflowService;

    public AgentRepairPipeline(DockerBuild dockerBuild, EnvConfig envConfig, boolean verbose) {
        this.dockerBuild = dockerBuild;
        this.envConfig = envConfig;
        this.verbose = verbose;
        this.changeImpactService = new ChangeImpactReportService(verbose, envConfig);
        this.gitWorkflowService = new GitWorkflowService();
    }

    @Override
    public List<Attempt> runRepairLoop(Path extractedPath, String projectName, String dockerImage,
            BreakingUpdateRecord record, Path commitReportDir, ClassificationSummary summary, Path outputBaseDir,
            Path initialLogFile) {

        log.info("Starting agent-based repair pipeline for {} (commit: {})",
                projectName, record.breakingCommit());

        Path projectDir = ProjectPaths.resolveProjectDir(extractedPath, projectName);

        // Initialize Git repository if not already initialized
        try {
            gitWorkflowService.initAndCommit(projectDir, "Initial commit - extracted project");
        } catch (Exception e) {
            log.warn("Git repository initialization failed or already exists: {}", e.getMessage());
        }

        // Step 1: Generate process ID for this repair execution
        String processId = ProcessIdService.generateShortProcessId();
        String fullProcessId = ProcessIdService.generateProcessId();
        log.info("Generated process ID: {} (full: {})", processId, fullProcessId);

        // Step 2: Prepare project for new process (commit changes, checkout main)
        try {
            gitWorkflowService.prepareForNewProcess(projectDir);
            log.info("Prepared project for new repair process");
        } catch (Exception e) {
            log.error("Failed to prepare project for new process: {}", e.getMessage(), e);
            // Return failure attempt for setup failure
            return createFailureAttempt(processId, commitReportDir, FailureCategory.UNKNOWN_FAILURE, "SETUP_FAILURE");
        }

        // Step 3: Create agent instance using factory (dynamic selection based on environment)
        BaseAgent agent;
        try {
            agent = AgentFactory.createAgent(envConfig);
            log.info("Created agent: {} (rule generator: {})", agent.getRuleGeneratorName(), agent.getLlmAgentName());
        } catch (Exception e) {
            log.error("Failed to create agent: {}", e.getMessage(), e);
            // Return failure attempt for agent creation failure
            return createFailureAttempt(processId, commitReportDir, FailureCategory.TRANSFORMATION_FAILURE, "AGENT_CREATION_FAILURE");
        }
        
        // Step 4: Create agent branch from main with process ID
        try {
            gitWorkflowService.createAgentBranch(projectDir, agent.getRuleGeneratorName(), processId);
            log.info("Created and checked out agent branch: agent-{}-{}", agent.getRuleGeneratorName(), processId);
        } catch (Exception e) {
            log.error("Failed to create agent branch: {}", e.getMessage(), e);
            // Return failure attempt for Git setup failure
            return createFailureAttempt(processId, commitReportDir, FailureCategory.UNKNOWN_FAILURE, "GIT_SETUP_FAILURE");
        }

        // Step 5: Ensure agent Docker image exists
        String dockerImageAgentName = agent.getDockerImageName();
        Path dockerfileDir = agent.getDockerfilePath();
        try {
            log.info("Ensuring agent image exists: {}", dockerImageAgentName);
            dockerBuild.ensureImageExistsOrBuildFromDockerfile(dockerImageAgentName, dockerfileDir);
            log.info("Docker image {} is ready", dockerImageAgentName);
        } catch (Exception e) {
            log.error("Error ensuring agent image exists", e);
            // Return failure attempt for Docker image/container setup failure
            return createFailureAttempt(processId, commitReportDir, FailureCategory.UNKNOWN_FAILURE, "DOCKER_IMAGE_FAILURE");
        }

        // Step 6: Find and prepare m2 folder for mounting
        Path m2Folder = dockerBuild.findM2Folder(projectDir);
        if (m2Folder != null) {
            log.info("Found m2 folder at: {}. It will be mounted to /root/.m2 in container", m2Folder);
        } else {
            log.info("M2 folder not found at {}. Maven will use default repository.", extractedPath.resolve("m2"));
        }

        // The project is now in the agent branch created above. We mount it directly without copying.
        String agentBranchName = "agent-" + agent.getRuleGeneratorName() + "-" + processId;
        log.info("Project directory {} is in agent branch {} for agent modifications", projectDir, agentBranchName);

        // Copy original error files to commitReportDir/original/ before applying agent changes
        // Follow the same logic as PROMPT_CLASSES pipeline (PromptGenerationService)
        if (commitReportDir != null) {
            Path classifierReport = commitReportDir.resolve("breaking-classifier-report.json");
            if (Files.exists(classifierReport)) {
                try {
                    // Generate change-impact to obtain file impacts (files with errors)
                    Path changeImpactPath = commitReportDir.resolve("input_change-impact.json");
                    List<ChangeImpactReportService.FileImpact> fileImpacts = changeImpactService.generateChangeImpactFromClassifierReport(
                            record,
                            summary,
                            outputBaseDir,
                            classifierReport,
                            changeImpactPath);
                    
                    if (fileImpacts != null && !fileImpacts.isEmpty()) {
                        Path originalDir = commitReportDir.resolve("original");
                        Files.createDirectories(originalDir);
                        
                        int copiedCount = 0;
                        for (ChangeImpactReportService.FileImpact fileImpact : fileImpacts) {
                            if (fileImpact == null || fileImpact.filePath() == null || fileImpact.filePath().isBlank()) {
                                continue;
                            }
                            
                            // Resolve original source file using same logic as PromptGenerationService
                            Path originalSource = resolveOriginalSourceFile(record, outputBaseDir, fileImpact.filePath());
                            if (originalSource == null || !Files.isRegularFile(originalSource)) {
                                log.warn("Could not resolve original source file for {} in {}", 
                                        fileImpact.filePath(), record.breakingCommit());
                                continue;
                            }
                            
                            // Copy using only the filename (not the full directory structure)
                            Path originalCopyTarget = originalDir.resolve(originalSource.getFileName());
                            try {
                                Files.copy(originalSource, originalCopyTarget, StandardCopyOption.REPLACE_EXISTING);
                                log.debug("Copied original source {} to {}", originalSource, originalCopyTarget);
                                copiedCount++;
                            } catch (IOException copyEx) {
                                log.warn("Failed to copy original source {} to {}: {}", 
                                        originalSource, originalCopyTarget, copyEx.getMessage());
                            }
                        }
                        
                        log.info("Copied {} original error files to {}", copiedCount, originalDir);
                    } else {
                        log.warn("No file impacts found to copy originals for commit {}", record.breakingCommit());
                    }
                } catch (Exception e) {
                    log.warn("Failed to copy original error files to commit directory: {}", e.getMessage());
                }
            } else {
                log.warn("breaking-classifier report not found at {}. Skipping copy of original error files.", classifierReport);
            }
        }

        // Step 5: Execute agent (delegated to agent - it handles everything internally)
        AgentExecutionRequest executionRequest = new AgentExecutionRequest(
                dockerBuild,
                projectDir,
                projectName,
                dockerImageAgentName,
                m2Folder,
                outputBaseDir,
                commitReportDir,
                this.verbose
        );
        
        AgentExecutionResult executionResult;
        try {
            executionResult = agent.execute(executionRequest);
            log.info("Agent execution completed. Success: {}, Compile: {}, Test: {}", 
                    executionResult.success(), executionResult.compileSuccess(), executionResult.testSuccess());
        } catch (IOException e) {
            log.error("Failed to execute agent: {}", e.getMessage(), e);
            // Return failure attempt for agent execution failure (container or agent error)
            // Check if it's likely a container issue (Docker command failure) vs agent error
            String errorMsg = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
            FailureCategory failureCategory;
            String failureReason;
            if (errorMsg.contains("docker") || errorMsg.contains("container") || 
                errorMsg.contains("cannot connect") || errorMsg.contains("timeout") ||
                errorMsg.contains("connection refused") || errorMsg.contains("network")) {
                failureCategory = FailureCategory.UNKNOWN_FAILURE;  // Container/Docker failure
                failureReason = "CONTAINER_EXECUTION_FAILURE";
            } else {
                failureCategory = FailureCategory.TRANSFORMATION_FAILURE;  // Agent command execution error
                failureReason = "AGENT_COMMAND_EXECUTION_FAILURE";
            }
            return createFailureAttempt(processId, commitReportDir, failureCategory, failureReason);
        }

        // Create attempt result based on breaking classifier analysis
        Attempt attempt = null;
        FailureCategory attemptCategory = FailureCategory.UNKNOWN_FAILURE;
        boolean attemptSuccessful = false;
        
        // Distinguish between agent execution failure and compilation failure after transformation
        // If agent.execute() succeeded but compileSuccess is false, it means:
        // - The agent command executed (no IOException)
        // - But the compilation failed after applying the transformation
        // This is different from AGENT_COMMAND_EXECUTION_FAILURE (IOException)
        if (!executionResult.compileSuccess() && executionResult.compileLogFile() != null) {
            log.warn("Agent command executed but compilation failed. This indicates a compilation error after transformation was applied.");
            // The attemptCategory will be determined from the log analysis below
            // But we know it's a compilation failure after transformation, not an agent execution error
        }

        // Step 6: Copy logs to commit report directory with distinct names
        if (commitReportDir != null) {
            // Copy compile log
            if (executionResult.compileLogFile() != null) {
                try {
                    Path compileLogTarget = commitReportDir.resolve("agent_compile_output.log");
                    Files.createDirectories(commitReportDir);
                    Files.copy(executionResult.compileLogFile(), compileLogTarget, StandardCopyOption.REPLACE_EXISTING);
                    log.info("Copied compile log to {}", compileLogTarget);
                } catch (IOException e) {
                    log.warn("Failed to copy compile log to commit directory: {}", e.getMessage());
                }
            }

            // First, check if the agent command itself failed (compileLogFile contains errors)
            // The compileLogFile is the log of the agent command execution
            // If it has errors, it means the agent command failed, not the compilation after transformation
            boolean agentCommandFailed = false;
            if (executionResult.compileLogFile() != null && Files.exists(executionResult.compileLogFile())) {
                try {
                    log.info("Analyzing agent command log (compileLogFile) to check for agent execution errors...");
                    github.chains.breakingclassifier.FailureCategory agentLogCategory = 
                            github.chains.breakingclassifier.ErrorReportAggregator.categorizeLog(executionResult.compileLogFile());
                    
                    // If the agent command log indicates failure (not BUILD_SUCCESS), it's an agent error
                    if (agentLogCategory != null && 
                        agentLogCategory != github.chains.breakingclassifier.FailureCategory.BUILD_SUCCESS) {
                        agentCommandFailed = true;
                        attemptCategory = FailureCategory.TRANSFORMATION_FAILURE;  // Agent command execution error
                        attemptSuccessful = false;
                        log.warn("Agent command execution failed. Category from agent log: {}. Setting category to TRANSFORMATION_FAILURE (agent error).", 
                                agentLogCategory);
                    } else {
                        log.info("Agent command executed successfully. Proceeding to analyze Maven test log.");
                    }
                } catch (Exception e) {
                    log.warn("Failed to analyze agent command log: {}. Assuming agent command succeeded.", e.getMessage());
                    // If we can't analyze, assume agent command succeeded and continue with normal flow
                }
            } else if (!executionResult.compileSuccess()) {
                // If compileSuccess is false but no log file, it's likely an agent error
                agentCommandFailed = true;
                attemptCategory = FailureCategory.TRANSFORMATION_FAILURE;
                attemptSuccessful = false;
                log.warn("Agent command failed (compileSuccess=false) but no log file available. Setting category to TRANSFORMATION_FAILURE (agent error).");
            }

            // Test log was already copied by the agent
            Path testLogTarget = executionResult.testLogFile();

            // Only analyze Maven test log if agent command succeeded
            // If agent command failed, we already set the category to TRANSFORMATION_FAILURE
            if (!agentCommandFailed && testLogTarget != null && Files.exists(testLogTarget)) {
                try {
                    log.info("Analyzing mvn test log category directly from log file...");
                    
                    // Use ErrorReportAggregator.categorizeLog() to directly analyze the log file
                    github.chains.breakingclassifier.FailureCategory logCategory = 
                            github.chains.breakingclassifier.ErrorReportAggregator.categorizeLog(testLogTarget);
                    
                    log.info("Log category determined directly from log file: {}", logCategory);
                    
                    // Convert breaking classifier FailureCategory to se.kth.models.FailureCategory
                    attemptCategory = convertFailureCategory(logCategory);
                    attemptSuccessful = executionResult.success();
                    
                    // Log distinction between agent execution failure and compilation failure
                    if (!executionResult.compileSuccess()) {
                        log.info("Compilation failed after agent transformation was applied. Category: {}", attemptCategory);
                    } else if (!executionResult.testSuccess()) {
                        log.info("Compilation succeeded but tests failed after agent transformation. Category: {}", attemptCategory);
                    }
                    
                    // Note: breaking-classifier-report.json was already generated in MainCli
                    // from the initial log (before applying rules). We don't regenerate it here
                    // because the test log is AFTER rules are applied and won't contain the original errors.
                    // Use the existing classifierReport that was generated before rules
                    Path classifierReport = commitReportDir.resolve("breaking-classifier-report.json");
                    if (!Files.exists(classifierReport)) {
                        log.warn("breaking-classifier-report.json not found. It should have been generated in MainCli before calling runRepairLoop.");
                    } else {
                        log.info("Using existing breaking-classifier-report.json from MainCli (generated before applying rules)");
                    }
                    
                    // Generate complete change-impact report (including breaking-changes.json)
                    // This follows the same pattern as MainCli
                    try {
                        changeImpactService.copyAndGenerate(record, summary, outputBaseDir, commitReportDir,
                                classifierReport);
                        log.info("Generated complete change-impact report (including breaking-changes.json)");
                    } catch (Exception e) {
                        log.warn("Failed to generate complete change-impact report: {}", e.getMessage());
                        // Fallback: Generate only change-impact from classifier report
                        try {
                            Path changeImpactPath = commitReportDir.resolve("input_change-impact.json");
                            List<ChangeImpactReportService.FileImpact> fileImpacts = 
                                    changeImpactService.generateChangeImpactFromClassifierReport(
                                            record,
                                            summary,
                                            outputBaseDir,
                                            classifierReport,
                                            changeImpactPath);
                            
                            if (fileImpacts != null && !fileImpacts.isEmpty()) {
                                log.info("Generated change-impact report with {} files with errors", fileImpacts.size());
                            } else {
                                log.warn("No file impacts generated from change-impact analysis");
                            }
                        } catch (Exception fallbackEx) {
                            log.warn("Failed to generate change-impact report from classifier report: {}", fallbackEx.getMessage());
                        }
                    }
                    
                    // Generate change-impact report for this attempt (similar to ModelRepairPipeline)
                    try {
                        changeImpactService.generateChangeImpactForAttempt(
                                record, summary, outputBaseDir, commitReportDir, 1);
                        log.info("Generated change-impact report for attempt 1");
                    } catch (Exception e) {
                        log.warn("Failed to generate change-impact report for attempt: {}", e.getMessage());
                    }
                } catch (Exception e) {
                    log.error("Failed to analyze mvn test log with breaking classifier: {}", e.getMessage(), e);
                    attemptCategory = FailureCategory.UNKNOWN_FAILURE;
                    attemptSuccessful = false;
                }
            } else if (!agentCommandFailed) {
                // Agent command succeeded but test log not available
                log.warn("Test log file not available for breaking classifier analysis");
                // If agent command succeeded but no test log, use UNKNOWN_FAILURE
                // (we already checked compileLogFile for agent errors above)
                if (attemptCategory == FailureCategory.UNKNOWN_FAILURE) {
                    attemptCategory = FailureCategory.UNKNOWN_FAILURE;
                }
                attemptSuccessful = false;
            }
            // If agentCommandFailed is true, we already set attemptCategory to TRANSFORMATION_FAILURE above
            
            // Create Attempt object (attempt 1 for agent pipeline - single attempt)
            if (commitReportDir != null) {
                attempt = new Attempt(
                        1, // attemptCount - agent pipeline has only one attempt
                        processId, // processId - links attempt to process
                        attemptCategory,
                        commitReportDir.toString(), // logFileParent
                        attemptSuccessful
                );
                log.info("Created attempt 1 (process: {}) - Category: {}, Success: {}", 
                        processId, attemptCategory, attemptSuccessful);
            }
        }

        // Step 7: Copy agent output/results from workspace to commitReportDir
        // Delegated to the agent - each agent knows how to copy its own results
        if (executionResult.workspaceDir() != null && Files.exists(executionResult.workspaceDir()) && commitReportDir != null) {
            try {
                agent.copyResults(executionResult.workspaceDir(), commitReportDir);
            } catch (Exception e) {
                log.warn("Failed to copy agent results from workspace: {}", e.getMessage());
            }
        }

        // Step 8: Cleanup workspace directory (temporary folder created for agent execution)
        if (executionResult.workspaceDir() != null && Files.exists(executionResult.workspaceDir())) {
            try {
                cleanupWorkspaceDirectory(executionResult.workspaceDir());
            } catch (Exception e) {
                log.warn("Failed to cleanup workspace directory {}: {}", executionResult.workspaceDir(), e.getMessage());
            }
        }

        // Return list with the attempt (following the same pattern as ModelRepairPipeline)
        // Always return at least one attempt - if attempt is null, create a failure attempt
        List<Attempt> attempts = new ArrayList<>();
        if (attempt != null) {
            attempts.add(attempt);
        } else {
            // This should not happen if everything worked, but create a failure attempt as fallback
            log.warn("No attempt was created for commit {} - creating failure attempt", record.breakingCommit());
            attempts.addAll(createFailureAttempt(processId, commitReportDir, FailureCategory.UNKNOWN_FAILURE, "NO_ATTEMPT_CREATED"));
        }
        
        log.info("Agent repair pipeline completed for {} (commit: {}). Total attempts: {}", 
                projectName, record.breakingCommit(), attempts.size());
        return attempts;
    }
    
    /**
     * Creates a failure attempt with a specific failure category and reason.
     * 
     * @param processId the process ID
     * @param commitReportDir the commit report directory (can be null)
     * @param failureCategory the failure category
     * @param reason the reason for the failure (for logging)
     * @return list with a single failure attempt
     */
    private List<Attempt> createFailureAttempt(String processId, Path commitReportDir, 
                                               FailureCategory failureCategory, String reason) {
        String logFileParent = commitReportDir != null ? commitReportDir.toString() : "";
        Attempt failureAttempt = new Attempt(
                1,
                processId,
                failureCategory,
                logFileParent,
                false);
        log.warn("Created failure attempt for reason: {} with category: {}", reason, failureCategory);
        return java.util.List.of(failureAttempt);
    }

    /**
     * Converts a breaking classifier FailureCategory to se.kth.models.FailureCategory.
     * 
     * @param classifierCategory the failure category from breaking classifier
     * @return the corresponding se.kth.models.FailureCategory
     */
    private FailureCategory convertFailureCategory(github.chains.breakingclassifier.FailureCategory classifierCategory) {
        if (classifierCategory == null) {
            return FailureCategory.UNKNOWN_FAILURE;
        }
        
        try {
            // Both enums have similar names, so we can use valueOf
            return FailureCategory.valueOf(classifierCategory.name());
        } catch (IllegalArgumentException e) {
            // Handle special cases
            if (classifierCategory == github.chains.breakingclassifier.FailureCategory.UNKNOWN) {
                return FailureCategory.UNKNOWN_FAILURE;
            }
            log.warn("Could not convert failure category: {}, defaulting to UNKNOWN_FAILURE", classifierCategory);
            return FailureCategory.UNKNOWN_FAILURE;
        }
    }

    /**
     * Resolves the path to an original source file from the extracted project.
     * Uses the same logic as PromptGenerationService.resolveOriginalSourceFile.
     * 
     * @param record the breaking update record
     * @param outputBaseDir the output base directory
     * @param filePath the relative file path (e.g., "src/main/java/Example.java")
     * @return the resolved Path to the original source file, or null if not found
     */
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
            log.debug("Failed to resolve original source file {} for {}: {}", 
                    filePath, record.breakingCommit(), e.getMessage());
        }
        return null;
    }

    /**
     * Cleans up the temporary workspace directory created for agent execution.
     * This directory is created with Files.createTempDirectory() and should be
     * deleted after copying results to the commit report directory.
     * 
     * @param workspaceDir the workspace directory to clean up
     */
    private void cleanupWorkspaceDirectory(Path workspaceDir) {
        if (workspaceDir == null || !Files.exists(workspaceDir)) {
            return;
        }
        
        try {
            // Check if it's a temp directory (created with createTempDirectory)
            String dirName = workspaceDir.getFileName().toString();
            if (dirName.startsWith("agent-workspace-")) {
                FileSystemUtils.deleteDirectory(workspaceDir);
                log.info("Cleaned up temporary workspace directory: {}", workspaceDir);
            } else {
                log.warn("Workspace directory {} does not appear to be a temporary directory. Skipping cleanup for safety.", workspaceDir);
            }
        } catch (IOException e) {
            log.warn("Failed to cleanup workspace directory {}: {}", workspaceDir, e.getMessage());
        }
    }
    
}
