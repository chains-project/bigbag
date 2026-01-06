package com.example.core.agent.impl;

import com.example.core.agent.BaseAgent;
import com.example.core.agent.model.AgentExecutionRequest;
import com.example.core.agent.model.AgentExecutionResult;
import com.example.core.config.EnvConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;

/**
 * Spoon-specific agent implementation.
 * Encapsulates all Spoon-related logic including:
 * - Spoon base template copying
 * - Spoon API documentation mounting
 * - Spoon-specific command generation
 * - Complete execution flow (setup, command execution, result collection)
 */
public class SpoonAgent extends BaseAgent {
    
    private static final Logger log = LoggerFactory.getLogger(SpoonAgent.class);
    
    // Environment variable names (paths to folders)
    private static final String BASE_TEMPLATE = "BASE_TEMPLATE";
    private static final String API_DOCS = "API_DOCS";
    
    // Container paths
    private static final String SPOON_BASE_FOLDER = "spoon-base-template";
    private static final String API_DOCS_FOLDER = "api-docs";
    private static final String CONTAINER_WORK_DIR = "/workspace";
    
    public SpoonAgent(EnvConfig envConfig, String llmAgentName) {
        super(envConfig, llmAgentName, "spoon");
    }
    
    @Override
    public void validateEnvironment() throws IllegalStateException {
        // Spoon agent requires BASE_TEMPLATE and API_DOCS
        // These are optional but recommended, so we only warn if missing
        getEnvPath(BASE_TEMPLATE).ifPresentOrElse(
            path -> {
                if (!Files.exists(path)) {
                    log.warn("BASE_TEMPLATE path does not exist: {}", path);
                }
            },
            () -> log.warn("BASE_TEMPLATE not set. Spoon agent may not function correctly.")
        );
        
        getEnvPath(API_DOCS).ifPresentOrElse(
            path -> {
                if (!Files.exists(path)) {
                    log.warn("API_DOCS path does not exist: {}", path);
                }
            },
            () -> log.warn("API_DOCS not set. Spoon agent may not have API documentation available.")
        );
    }
    
    @Override
    public AgentExecutionResult execute(AgentExecutionRequest request) throws IOException {
        log.info("Executing Spoon agent for project: {}", request.projectName());
        
        // Step 1: Setup workspace and mounts
        Path workspaceDir = setupWorkspace(request);
        Path spoonDocsFolder = extractSpoonDocsFromMounts(request);
        
        // Step 2: Prepare environment variables
        Map<String, String> envVars = prepareEnvironmentVariables();
        
        // Step 3: Create log files
        Path compileLogFile = request.outputBaseDir().resolve("maven_compile_output.log");
        
        // Step 4: Build and execute the main agent command
        String agentCommand = buildAgentCommand(request.projectName(), CONTAINER_WORK_DIR);
        log.info("Executing Spoon agent command in container {}", request.dockerImageName());
        
        boolean compileSuccess = request.dockerBuild().executeMavenCommandInContainerWithWorkspace(
                request.dockerImageName(),
                workspaceDir,
                request.projectDir(),
                request.projectName(),
                CONTAINER_WORK_DIR,
                agentCommand,
                compileLogFile,
                envVars.isEmpty() ? null : envVars,
                request.m2Folder(),
                spoonDocsFolder,
                request.verbose()
        );
        
        log.info("Agent command execution completed. Success: {}. Log saved to: {}", compileSuccess, compileLogFile);
        
        // Step 5: Execute test command
        Path testLogFile = executeTestCommand(request, workspaceDir, envVars, spoonDocsFolder);
        boolean testSuccess = testLogFile != null && Files.exists(testLogFile);
        
        // Step 6: Copy agent execution log
        Path agentExecutionLog = copyAgentExecutionLog(request.projectDir(), request.commitReportDir());
        
        return new AgentExecutionResult(
                compileSuccess && testSuccess,
                workspaceDir,
                compileLogFile,
                testLogFile,
                agentExecutionLog,
                compileSuccess,
                testSuccess
        );
    }
    
    /**
     * Sets up the workspace and prepares mounts.
     */
    private Path setupWorkspace(AgentExecutionRequest request) throws IOException {
        log.info("Setting up Spoon agent workspace for project: {}", request.projectName());
        
        Path workspaceDir = createWorkspaceDirectory();
        
        // Copy Spoon base template to workspace/spoon-base-template
        getEnvPath(BASE_TEMPLATE).ifPresent(spoonBaseTemplate -> {
            if (Files.exists(spoonBaseTemplate)) {
                Path spoonBaseTarget = workspaceDir.resolve(SPOON_BASE_FOLDER);
                try {
                    copyDirectory(spoonBaseTemplate, spoonBaseTarget);
                    log.info("Copied Spoon base template from {} to {}", spoonBaseTemplate, spoonBaseTarget);
                } catch (IOException e) {
                    log.warn("Failed to copy Spoon base template: {}", e.getMessage());
                }
            } else {
                log.warn("Spoon base template path does not exist: {}", spoonBaseTemplate);
            }
        });
        
        return workspaceDir;
    }
    
    /**
     * Extracts Spoon docs folder from environment for DockerBuild compatibility.
     */
    private Path extractSpoonDocsFromMounts(AgentExecutionRequest request) {
        return getEnvPath(API_DOCS)
                .filter(Files::exists)
                .orElse(null);
    }
    
    /**
     * Prepares environment variables for container execution.
     */
    private Map<String, String> prepareEnvironmentVariables() {
        Map<String, String> envVars = new HashMap<>();
        
        // Add LLM API key if available
        getEnv("LLM_API_KEY").ifPresent(key -> {
            envVars.put("GEMINI_API_KEY", key);
            envVars.put("GOOGLE_API_KEY", key);
        });
        
        return envVars;
    }
    
    /**
     * Builds the Spoon-specific agent command.
     */
    private String buildAgentCommand(String projectName, String workspaceDir) {
        String spoonBaseFullPath = workspaceDir + "/" + SPOON_BASE_FOLDER;
        String apiDocsPath = workspaceDir + "/" + API_DOCS_FOLDER;
        
        return String.format(
            "gemini --model gemini-3-pro-preview --debug --yolo \" 'Project @%s/ does not compile. Plan: "
          + "1) Run `mvn compile` in the project @%s/ to get the compilation errors only. "
          + "2) Generate a Spoon source code transformation to fix the errors. "
          + "   - Use the project in folder %s/ as the base project template. "
          + "   - Only modify the files that are causing the compilation errors. "
          + "   - Save the transformation rules inside the folder @%s/, e.g., in `%s/src/main/java/github/chains/processors/`. "
          + "   - Use the Spoon API documentation located in folder @%s/ for reference. "
          + "3) Ensure the generated transformation file compiles correctly. "
          + "4) Apply the transformation to fix the compilation errors. "
          + "5) Verify that the project now compiles successfully with `mvn compile`. "
          + "> /%s/agent_execution.log 2>&1'\"",
            projectName, projectName, SPOON_BASE_FOLDER, spoonBaseFullPath, spoonBaseFullPath, apiDocsPath, projectName
        );
    }
    
    /**
     * Executes the test command and returns the test log file path.
     */
    private Path executeTestCommand(AgentExecutionRequest request, Path workspaceDir, 
                                   Map<String, String> envVars, Path spoonDocsFolder) {
        String projectPathInContainer = CONTAINER_WORK_DIR + "/" + request.projectName();
        String testCommand = String.format("cd %s && mvn compile 2>&1 | tee mavenTest.log", projectPathInContainer);
        
        Path tempTestLogFile = request.outputBaseDir().resolve("temp_maven_test.log");
        
        request.dockerBuild().executeMavenCommandInContainerWithWorkspace(
                request.dockerImageName(),
                workspaceDir,
                request.projectDir(),
                request.projectName(),
                CONTAINER_WORK_DIR,
                testCommand,
                tempTestLogFile,
                envVars.isEmpty() ? null : envVars,
                request.m2Folder(),
                spoonDocsFolder,
                request.verbose()
        );
        
        // Copy the actual test log from project directory
        Path testLogFile = request.commitReportDir() != null
                ? request.commitReportDir().resolve("maven_test_output.log")
                : request.outputBaseDir().resolve("maven_test_output.log");
        
        try {
            Path testLogSource = request.projectDir().resolve("mavenTest.log");
            if (Files.exists(testLogSource)) {
                Files.createDirectories(testLogFile.getParent());
                Files.copy(testLogSource, testLogFile, StandardCopyOption.REPLACE_EXISTING);
                log.info("Copied mvn test log from project directory to {}", testLogFile);
                return testLogFile;
            } else {
                log.warn("mvn test log not found at {}. Test may have failed to save log.", testLogSource);
            }
        } catch (IOException e) {
            log.warn("Failed to copy mvn test log from project directory: {}", e.getMessage());
        }
        
        return null;
    }
    
    /**
     * Copies the agent execution log from project directory.
     */
    private Path copyAgentExecutionLog(Path projectDir, Path commitReportDir) {
        if (commitReportDir == null) {
            return null;
        }
        
        try {
            Path agentLogSource = projectDir.resolve("agent_execution.log");
            if (Files.exists(agentLogSource)) {
                Path agentLogTarget = commitReportDir.resolve("agent_execution.log");
                Files.createDirectories(commitReportDir);
                Files.copy(agentLogSource, agentLogTarget, StandardCopyOption.REPLACE_EXISTING);
                log.info("Copied agent execution log to {}", agentLogTarget);
                return agentLogTarget;
            } else {
                log.debug("Agent execution log not found at {}", agentLogSource);
            }
        } catch (IOException e) {
            log.warn("Failed to copy agent execution log to commit directory: {}", e.getMessage());
        }
        
        return null;
    }
    
    @Override
    public void copyResults(Path workspaceDir, Path commitReportDir) throws IOException {
        if (workspaceDir == null || commitReportDir == null) {
            log.warn("Cannot copy Spoon results: workspaceDir or commitReportDir is null");
            return;
        }
        
        Path workspaceSpoonBase = workspaceDir.resolve(SPOON_BASE_FOLDER);
        if (Files.exists(workspaceSpoonBase)) {
            Files.createDirectories(commitReportDir);
            Path spoonBaseTarget = commitReportDir.resolve("spoon-base-template");
            
            // Delete existing directory if it exists
            if (Files.exists(spoonBaseTarget)) {
                Files.walkFileTree(spoonBaseTarget, new java.nio.file.SimpleFileVisitor<Path>() {
                    @Override
                    public java.nio.file.FileVisitResult visitFile(Path file, java.nio.file.attribute.BasicFileAttributes attrs) throws IOException {
                        Files.deleteIfExists(file);
                        return java.nio.file.FileVisitResult.CONTINUE;
                    }
                    @Override
                    public java.nio.file.FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                        Files.deleteIfExists(dir);
                        return java.nio.file.FileVisitResult.CONTINUE;
                    }
                });
            }
            
            copyDirectory(workspaceSpoonBase, spoonBaseTarget);
            log.info("Copied spoon-base-template (with generated rules) from workspace {} to {}", 
                    workspaceSpoonBase, spoonBaseTarget);
        } else {
            log.warn("spoon-base-template not found in workspace at {}. Skipping copy.", workspaceSpoonBase);
        }
    }
}

