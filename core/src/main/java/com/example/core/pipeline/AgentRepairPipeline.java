package com.example.core.pipeline;

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
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Agent-based repair pipeline implementation.
 * This is a placeholder for future implementation of agent-based repair
 * strategies.
 * Currently returns an empty list of attempts.
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

        // Get agent name from environment variable
        String agentName = envConfig.get("AGENT_NAME").orElse("unknown");
        
        // Create agent branch: agent-{AGENT_NAME}
        String agentBranchName = "agent-" + agentName;
        try {
            gitWorkflowService.createAndCheckoutBranch(projectDir, agentBranchName);
            log.info("Created and checked out agent branch: {}", agentBranchName);
        } catch (Exception e) {
            log.error("Failed to create/checkout agent branch {}: {}", agentBranchName, e.getMessage(), e);
            return new ArrayList<>();
        }

        String agentImage = agentName;

        Path dockerfileDir = Path.of("images/" + agentImage + "/Dockerfile");

        String dockerImageAgentName = agentImage + ":latest";

        try {
            log.info("Ensuring agent image exists");
            dockerBuild.ensureImageExistsOrBuildFromDockerfile(dockerImageAgentName, dockerfileDir);
            log.info("Docker image {} is ready", agentImage);

        } catch (Exception e) {
            log.error("Error ensuring agent image exists", e);
        }

        // Find and prepare m2 folder for mounting
        // The m2 folder is typically at extractedPath/m2 (where extractedPath contains
        // both project and m2)
        // findM2Folder looks for m2 in the parent directory of projectDir
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

        // Create workspace directory structure for spoon-base-template (copied)
        // Structure: workspace/
        //   - {projectName}/  (mounted from projectDir - already in correct branch)
        //   - spoon-base-template/  (copied)
        //   - api-docs/  (mounted)
        Path workspaceDir = null;
        Path spoonBaseTemplate = envConfig.getPath("SPOON_BASE_TEMPLATE").orElse(null);
        try {
            workspaceDir = Files.createTempDirectory("agent-workspace-");
            log.info("Created workspace directory at: {}", workspaceDir);
            
            // Copy Spoon base template to workspace/spoon-base-template (project is mounted directly)
            if (spoonBaseTemplate != null && Files.exists(spoonBaseTemplate)) {
                Path spoonBaseTarget = workspaceDir.resolve("spoon-base-template");
                copyDirectory(spoonBaseTemplate, spoonBaseTarget);
                log.info("Copied Spoon base template from {} to {}", spoonBaseTemplate, spoonBaseTarget);
            } else {
                log.warn("Spoon base template path not provided or does not exist. SPOON_BASE_TEMPLATE={}", spoonBaseTemplate);
            }
        } catch (IOException e) {
            log.error("Failed to create workspace directory structure: {}", e.getMessage(), e);
        }

        // Get Spoon API documentation path for mounting
        Path spoonApiDocs = envConfig.getPath("SPOON_API_DOCS").orElse(null);
        if (spoonApiDocs == null || !Files.exists(spoonApiDocs)) {
            log.warn("Spoon API docs path not provided or does not exist. SPOON_API_DOCS={}", spoonApiDocs);
        }

        // Execute command in the workspace
        String containerWorkDir = "/workspace"; // Working directory in the agent container
        
        // Build the command with proper folder references
        // In workspace: project is at /{projectName}/, spoon-base-template is at /spoon-base-template/, api-docs is at /api-docs/
        String spoonBaseFolder = "spoon-base-template"; // Folder name in workspace
        String apiDocsFolder = "api-docs"; // Folder name in workspace (mounted)
        
        // Format the command: gemini expects the format: gemini --debug --yolo " execute in /path/ 'command'"
        // The inner command is in single quotes, wrapped in double quotes
        // IMPORTANT: The agent must save transformation rules in /workspace/spoon-base-template/
        String spoonBaseFullPath = containerWorkDir + "/" + spoonBaseFolder; // /workspace/spoon-base-template
        String mavenCommand = String.format(
            "gemini --model gemini-3-pro-preview --debug --yolo \" 'Project /%s/ does not compile. Plan: "
          + "1) Run `mvn compile` in the project /%s/ to get the compilation errors only. "
          + "2) Generate a Spoon source code transformation to fix the errors. "
          + "   - Use the project in folder %s/ as the base project template. "
          + "   - Only modify the files that are causing the compilation errors. "
          + "   - Save the transformation rules inside the folder %s/, e.g., in `%s/src/main/java/github/chains/processors/`. "
          + "   - Use the Spoon API documentation located in folder %s/ for reference. "
          + "3) Ensure the generated transformation file compiles correctly. "
          + "4) Apply the transformation to fix the compilation errors. "
          + "5) Verify that the project now compiles successfully with `mvn compile`. "
          + "> /%s/agent_execution.log 2>&1'\"",
            projectName, projectName, spoonBaseFolder, spoonBaseFullPath, spoonBaseFullPath, apiDocsFolder, projectName
        );
        
        
        // Create log file for the compile command execution
        // Use outputBaseDir to create an absolute path so getParent() works correctly
        Path compileLogFile = outputBaseDir.resolve("maven_compile_output.log");

        // Prepare environment variables for Gemini (and other needed vars)
        Map<String, String> envVars = new HashMap<>();

        // Add Gemini API key if available
        envConfig.get("LLM_API_KEY").ifPresent(key -> envVars.put("GEMINI_API_KEY", key));
        envConfig.get("LLM_API_KEY").ifPresent(key -> envVars.put("GOOGLE_API_KEY", key));

        // Add any other environment variables that might be needed
        // You can add more here as needed, e.g.:
        // envConfig.get("OTHER_ENV_VAR").ifPresent(value ->
        // envVars.put("OTHER_ENV_VAR", value));

        if (workspaceDir == null) {
            log.error("Workspace directory was not created. Cannot proceed with execution.");
            return new ArrayList<>();
        }

        log.info("Executing '{}' in agent container {} (project: {} -> /workspace/{}, workspace: {}, workDir: {}, env vars: {}, m2: {}, spoon docs: {})",
                mavenCommand, dockerImageAgentName, projectDir, projectName, workspaceDir, containerWorkDir, envVars.size(),
                m2Folder != null ? "mounted" : "not mounted",
                spoonApiDocs != null ? "mounted" : "not mounted");

        // Use executeMavenCommandInContainerWithWorkspace to mount:
        // 1. Workspace (with spoon-base-template copied) at /workspace
        // 2. Project (already in correct branch) at /workspace/{projectName}/
        // 3. Spoon docs at /workspace/api-docs/
        // 4. M2 at /root/.m2
        boolean compileSuccess = dockerBuild.executeMavenCommandInContainerWithWorkspace(
                dockerImageAgentName,
                workspaceDir, // Workspace with spoon-base-template
                projectDir, // Project directory (already in correct branch) - mounted directly
                projectName, // Project name for mount path
                containerWorkDir,
                mavenCommand,
                compileLogFile,
                envVars.isEmpty() ? null : envVars,
                m2Folder,
                spoonApiDocs,
                this.verbose
        );

        log.info("Maven compile execution completed. Success: {}. Log saved to: {}",
                compileSuccess, compileLogFile);

        // After compile command, run mvn test inside /workspace/{projectName}
        // The command will save its log to mavenTest.log in the project directory
        // The workspace is at /workspace, and project is at /workspace/{projectName}
        // So we need to execute: cd /workspace/{projectName} && mvn test > mavenTest.log 2>&1
        String projectPathInContainer = containerWorkDir.endsWith("/")
                ? containerWorkDir + projectName
                : containerWorkDir + "/" + projectName;
        // Include tee in the command to save log to mavenTest.log in the project directory
        String testCommand = String.format("cd %s && mvn test 2>&1 | tee mavenTest.log", projectPathInContainer);

        // Execute test command using the same method as gemini command
        // The log will be saved as mavenCompile.log by DockerBuild (we'll copy the actual log from project directory)
        Path tempTestLogFile = outputBaseDir.resolve("temp_maven_test.log");
        boolean testSuccess = dockerBuild.executeMavenCommandInContainerWithWorkspace(
                dockerImageAgentName,
                workspaceDir,
                projectDir,
                projectName,
                containerWorkDir, // Keep workspace as base directory
                testCommand, // Command includes cd and tee to save log in project directory
                tempTestLogFile, // Temporary log file (we'll copy the actual mavenTest.log from project)
                envVars.isEmpty() ? null : envVars,
                m2Folder,
                spoonApiDocs,
                this.verbose
        );

        log.info("Maven test execution completed. Success: {}", testSuccess);
        
        // Copy the actual test log from project directory (where mvn test saved it with tee)
        Path testLogFile = commitReportDir != null
                ? commitReportDir.resolve("maven_test_output.log")
                : outputBaseDir.resolve("maven_test_output.log");
        try {
            Path testLogSource = projectDir.resolve("mavenTest.log");
            if (Files.exists(testLogSource)) {
                Files.createDirectories(testLogFile.getParent());
                Files.copy(testLogSource, testLogFile, StandardCopyOption.REPLACE_EXISTING);
                log.info("Copied mvn test log from project directory to {}", testLogFile);
            } else {
                log.warn("mvn test log not found at {}. Test may have failed to save log.", testLogSource);
            }
        } catch (IOException e) {
            log.warn("Failed to copy mvn test log from project directory: {}", e.getMessage());
        }

        // Create attempt result based on breaking classifier analysis
        Attempt attempt = null;
        FailureCategory attemptCategory = FailureCategory.UNKNOWN_FAILURE;
        boolean attemptSuccessful = false;

        // Copy all logs to commit report directory with distinct names
        if (commitReportDir != null) {
            // Copy agent execution log (from project directory)
            try {
                Path agentLogSource = projectDir.resolve("agent_execution.log");
                if (Files.exists(agentLogSource)) {
                    Path agentLogTarget = commitReportDir.resolve("agent_execution.log");
                    Files.createDirectories(commitReportDir);
                    Files.copy(agentLogSource, agentLogTarget, StandardCopyOption.REPLACE_EXISTING);
                    log.info("Copied agent execution log to {}", agentLogTarget);
                } else {
                    log.debug("Agent execution log not found at {}", agentLogSource);
                }
            } catch (IOException e) {
                log.warn("Failed to copy agent execution log to commit directory: {}", e.getMessage());
            }

            // Copy compile log
            try {
                Path compileLogTarget = commitReportDir.resolve("maven_compile_output.log");
                Files.createDirectories(commitReportDir);
                Files.copy(compileLogFile, compileLogTarget, StandardCopyOption.REPLACE_EXISTING);
                log.info("Copied compile log to {}", compileLogTarget);
            } catch (IOException e) {
                log.warn("Failed to copy compile log to commit directory: {}", e.getMessage());
            }

            // Test log was already copied above (right after mvn test execution)
            Path testLogTarget = testLogFile;

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
                    attemptSuccessful = (attemptCategory == FailureCategory.BUILD_SUCCESS);
                    
                    // Also generate the full breaking classifier report for completeness
                    Path classifierReport = commitReportDir.resolve("breaking-classifier-report.json");
                    github.chains.breakingclassifier.BreakingClassifierApp classifierApp = 
                            new github.chains.breakingclassifier.BreakingClassifierApp();
                    github.chains.breakingclassifier.BreakingReport breakingReport = 
                            classifierApp.analyzeLog(testLogTarget, classifierReport);

                    if (breakingReport != null) {
                        log.info("Breaking classifier full analysis completed. Category: {}, Files with errors: {}",
                                breakingReport.failureCategory(), breakingReport.errorsByFile().size());
                    } else {
                        log.warn("Breaking classifier analysis returned null report");
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

        // Copy spoon-base-template from workspace (with generated rules) to commitReportDir/spoon-base-template
        // IMPORTANT: Copy from workspaceDir (which has the rules generated by the agent) not from the original template
        // Use commitReportDir directly (already points to the correct commit directory)
        if (workspaceDir != null && Files.exists(workspaceDir) && commitReportDir != null) {
            try {
                Path workspaceSpoonBase = workspaceDir.resolve("spoon-base-template");
                if (Files.exists(workspaceSpoonBase)) {
                    Files.createDirectories(commitReportDir);
                    
                    Path spoonBaseTarget = commitReportDir.resolve("spoon-base-template");
                    if (Files.exists(spoonBaseTarget)) {
                        // Delete existing directory if it exists
                        Files.walkFileTree(spoonBaseTarget, new SimpleFileVisitor<Path>() {
                            @Override
                            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                                Files.deleteIfExists(file);
                                return FileVisitResult.CONTINUE;
                            }
                            @Override
                            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                                Files.deleteIfExists(dir);
                                return FileVisitResult.CONTINUE;
                            }
                        });
                    }
                    copyDirectory(workspaceSpoonBase, spoonBaseTarget);
                    log.info("Copied spoon-base-template (with generated rules) from workspace {} to {}", workspaceSpoonBase, spoonBaseTarget);
                } else {
                    log.warn("spoon-base-template not found in workspace at {}. Skipping copy.", workspaceSpoonBase);
                }
            } catch (IOException e) {
                log.warn("Failed to copy spoon-base-template from workspace to repair_pipeline directory: {}", e.getMessage());
            }
        } else {
            log.warn("Workspace directory not available. Cannot copy spoon-base-template with generated rules.");
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
     * Copies a directory recursively from source to target.
     * 
     * @param source the source directory
     * @param target the target directory (will be created if it doesn't exist)
     * @throws IOException if an I/O error occurs
     */
    private void copyDirectory(Path source, Path target) throws IOException {
        Files.walkFileTree(source, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Path targetDir = target.resolve(source.relativize(dir));
                Files.createDirectories(targetDir);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Path targetFile = target.resolve(source.relativize(file));
                Files.copy(file, targetFile, StandardCopyOption.REPLACE_EXISTING);
                return FileVisitResult.CONTINUE;
            }
        });
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
