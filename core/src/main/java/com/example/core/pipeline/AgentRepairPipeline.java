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

        // Step 1: Create agent instance using factory (dynamic selection based on environment)
        BaseAgent agent;
        try {
            agent = AgentFactory.createAgent(envConfig);
            log.info("Created agent: {}", agent.getName());
        } catch (Exception e) {
            log.error("Failed to create agent: {}", e.getMessage(), e);
            return new ArrayList<>();
        }
        
        // Step 2: Create agent branch: agent-{AGENT_NAME}
        String agentBranchName = "agent-" + agent.getName();
        try {
            gitWorkflowService.createAndCheckoutBranch(projectDir, agentBranchName);
            log.info("Created and checked out agent branch: {}", agentBranchName);
        } catch (Exception e) {
            log.error("Failed to create/checkout agent branch {}: {}", agentBranchName, e.getMessage(), e);
            return new ArrayList<>();
        }

        // Step 3: Ensure agent Docker image exists
        String dockerImageAgentName = agent.getDockerImageName();
        Path dockerfileDir = agent.getDockerfilePath();
        try {
            log.info("Ensuring agent image exists: {}", dockerImageAgentName);
            dockerBuild.ensureImageExistsOrBuildFromDockerfile(dockerImageAgentName, dockerfileDir);
            log.info("Docker image {} is ready", dockerImageAgentName);
        } catch (Exception e) {
            log.error("Error ensuring agent image exists", e);
            return new ArrayList<>();
        }

        // Step 4: Find and prepare m2 folder for mounting
        Path m2Folder = dockerBuild.findM2Folder(projectDir);
        if (m2Folder != null) {
            log.info("Found m2 folder at: {}. It will be mounted to /root/.m2 in container", m2Folder);
        } else {
            log.info("M2 folder not found at {}. Maven will use default repository.", extractedPath.resolve("m2"));
        }

        // The project is now in the agent branch created above. We mount it directly without copying.
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
            return new ArrayList<>();
        }

        // Create attempt result based on breaking classifier analysis
        Attempt attempt = null;
        FailureCategory attemptCategory = FailureCategory.UNKNOWN_FAILURE;
        boolean attemptSuccessful = false;

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

            // Test log was already copied by the agent
            Path testLogTarget = executionResult.testLogFile();

            // Analyze mvn test log with breaking classifier to determine failure category
            // Use the direct log categorization method that analyzes the log file content
            if (testLogTarget != null && Files.exists(testLogTarget)) {
                try {
                    log.info("Analyzing mvn test log category directly from log file...");
                    
                    // Use ErrorReportAggregator.categorizeLog() to directly analyze the log file
                    github.chains.breakingclassifier.FailureCategory logCategory = 
                            github.chains.breakingclassifier.ErrorReportAggregator.categorizeLog(testLogTarget);
                    
                    log.info("Log category determined directly from log file: {}", logCategory);
                    
                    // Convert breaking classifier FailureCategory to se.kth.models.FailureCategory
                    attemptCategory = convertFailureCategory(logCategory);
                    attemptSuccessful = executionResult.success();
                    
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
            } else {
                log.warn("Test log file not available for breaking classifier analysis");
                attemptCategory = FailureCategory.UNKNOWN_FAILURE;
                attemptSuccessful = false;
            }
            
            // Create Attempt object (attempt 1 for agent pipeline - single attempt)
            if (commitReportDir != null) {
                attempt = new Attempt(
                        1, // attemptCount - agent pipeline has only one attempt
                        attemptCategory,
                        commitReportDir.toString(), // logFileParent
                        attemptSuccessful
                );
                log.info("Created attempt 1 - Category: {}, Success: {}", attemptCategory, attemptSuccessful);
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

        // Return list with the attempt (following the same pattern as ModelRepairPipeline)
        List<Attempt> attempts = new ArrayList<>();
        if (attempt != null) {
            attempts.add(attempt);
        }
        
        log.info("Agent repair pipeline completed for {} (commit: {}). Total attempts: {}", 
                projectName, record.breakingCommit(), attempts.size());
        return attempts;
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
    
}
