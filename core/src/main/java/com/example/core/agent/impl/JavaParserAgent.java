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
 * JavaParser-specific agent implementation.
 * Encapsulates all JavaParser-related logic including:
 * - JavaParser base template copying
 * - JavaParser API documentation mounting (optional)
 * - JavaParser-specific command generation
 * - Complete execution flow (setup, command execution, result collection)
 * 
 * JavaParser is a library for parsing, analyzing, and transforming Java source
 * code.
 * Unlike Spoon, JavaParser works directly with AST nodes and provides a simpler
 * API.
 */
public class JavaParserAgent extends BaseAgent {

    private static final Logger log = LoggerFactory.getLogger(JavaParserAgent.class);

    // Environment variable names (paths to folders)
    private static final String BASE_TEMPLATE = "BASE_TEMPLATE";
    private static final String API_DOCS = "API_DOCS"; // Optional

    // Container paths
    private static final String JAVAPARSER_BASE_FOLDER = "javaparser-base-template";
    private static final String API_DOCS_FOLDER = "javaparser-api-docs";
    private static final String CONTAINER_WORK_DIR = "/workspace";

    public JavaParserAgent(EnvConfig envConfig, String llmAgentName) {
        super(envConfig, llmAgentName, "javaparser");
    }

    @Override
    public void validateEnvironment() throws IllegalStateException {
        // JavaParser agent requires BASE_TEMPLATE
        // API_DOCS is optional but recommended
        getEnvPath(BASE_TEMPLATE).ifPresentOrElse(
                path -> {
                    if (!Files.exists(path)) {
                        log.warn("BASE_TEMPLATE path does not exist: {}", path);
                    }
                },
                () -> log.warn("BASE_TEMPLATE not set. JavaParser agent may not function correctly."));

        getEnvPath(API_DOCS).ifPresentOrElse(
                path -> {
                    if (!Files.exists(path)) {
                        log.warn("API_DOCS path does not exist: {}", path);
                    }
                },
                () -> log.debug("API_DOCS not set. JavaParser agent will work without API documentation."));
    }

    @Override
    public AgentExecutionResult execute(AgentExecutionRequest request) throws IOException {
        log.info("Executing JavaParser agent for project: {}", request.projectName());

        // Step 1: Setup workspace and mounts
        Path workspaceDir = setupWorkspace(request);
        Path javaparserDocsFolder = extractJavaParserDocsFromMounts(request);

        // Step 2: Prepare environment variables
        Map<String, String> envVars = prepareEnvironmentVariables();

        // Step 3: Create log files
        Path compileLogFile = request.outputBaseDir().resolve("maven_compile_output.log");

        // Step 4: Build and execute the main agent command
        String agentCommand = buildAgentCommand(request.projectName(), CONTAINER_WORK_DIR);
        log.info("Executing JavaParser agent command in container {}", request.dockerImageName());

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
                javaparserDocsFolder,
                request.verbose());

        log.info("Agent command execution completed. Success: {}. Log saved to: {}", compileSuccess, compileLogFile);

        // Step 5: Execute test command
        Path testLogFile = executeTestCommand(request, workspaceDir, envVars, javaparserDocsFolder);
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
                testSuccess);
    }

    /**
     * Sets up the workspace and prepares mounts.
     */
    private Path setupWorkspace(AgentExecutionRequest request) throws IOException {
        log.info("Setting up JavaParser agent workspace for project: {}", request.projectName());

        Path workspaceDir = createWorkspaceDirectory();

        // Copy base template to workspace/javaparser-base-template
        getEnvPath(BASE_TEMPLATE).ifPresent(baseTemplate -> {
            if (Files.exists(baseTemplate)) {
                Path javaparserBaseTarget = workspaceDir.resolve(JAVAPARSER_BASE_FOLDER);
                try {
                    copyDirectory(baseTemplate, javaparserBaseTarget);
                    log.info("Copied base template from {} to {}", baseTemplate, javaparserBaseTarget);
                } catch (IOException e) {
                    log.warn("Failed to copy base template: {}", e.getMessage());
                }
            } else {
                log.warn("Base template path does not exist: {}", baseTemplate);
            }
        });

        return workspaceDir;
    }

    /**
     * Extracts JavaParser docs folder from environment for DockerBuild
     * compatibility.
     */
    private Path extractJavaParserDocsFromMounts(AgentExecutionRequest request) {
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

        // Add LLM model if available
        getEnv("LLM_MODEL").ifPresent(model -> envVars.put("LLM_MODEL", model));

        return envVars;
    }

    /**
     * Builds the JavaParser-specific agent command.
     */
    private String buildAgentCommand(String projectName, String workspaceDir) {
        String javaparserBaseFullPath = workspaceDir + "/" + JAVAPARSER_BASE_FOLDER;
        String apiDocsPath = workspaceDir + "/" + API_DOCS_FOLDER;

        // Use the LLM agent name from the parent class
        String llmCommand = llmAgentName;
        String model = getEnv("LLM_MODEL").orElse("gemini-3.1-pro-preview");

        return String.format(
                "%s --model %s -o json --debug --yolo -p \" 'Project @%s/ does not compile. Plan: "
                        + "1) Run mvn compile in the project @%s/ to get the compilation errors only. "
                        + "2) Generate a source code transformation with JavaParserto fix the errors. "
                        + "   - Use the project in folder @%s/ as the base project template. "
                        + "   - Use JavaParser AST manipulation to create the transformation. "
                        + "   - Save the transformation code inside the folder @%s/, e.g., in %s/src/main/java/github/chains/Main.java "
                        + "   - Use the JavaParser API documentation located in folder @%s/ for reference. "
                        + "3) Ensure the generated transformation code compiles correctly. "
                        + "4) Apply the transformation to fix the compilation errors. "
                        + "5) Verify that the project now compiles successfully with mvn compile. "
                        + "2>&1 | tee %s/%s/agent_execution.log'\"",
                llmCommand, model, projectName, projectName, JAVAPARSER_BASE_FOLDER, javaparserBaseFullPath,
                javaparserBaseFullPath, apiDocsPath, workspaceDir, projectName);
    }

    /**
     * Executes the test command and returns the test log file path.
     */
    private Path executeTestCommand(AgentExecutionRequest request, Path workspaceDir,
            Map<String, String> envVars, Path javaparserDocsFolder) {
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
                javaparserDocsFolder,
                request.verbose());

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
            log.warn("Cannot copy JavaParser results: workspaceDir or commitReportDir is null");
            return;
        }

        Path workspaceJavaParserBase = workspaceDir.resolve(JAVAPARSER_BASE_FOLDER);
        if (Files.exists(workspaceJavaParserBase)) {
            Files.createDirectories(commitReportDir);
            Path javaparserBaseTarget = commitReportDir.resolve("javaparser-base-template");

            // Delete existing directory if it exists
            if (Files.exists(javaparserBaseTarget)) {
                Files.walkFileTree(javaparserBaseTarget, new java.nio.file.SimpleFileVisitor<Path>() {
                    @Override
                    public java.nio.file.FileVisitResult visitFile(Path file,
                            java.nio.file.attribute.BasicFileAttributes attrs) throws IOException {
                        Files.deleteIfExists(file);
                        return java.nio.file.FileVisitResult.CONTINUE;
                    }

                    @Override
                    public java.nio.file.FileVisitResult postVisitDirectory(Path dir, IOException exc)
                            throws IOException {
                        Files.deleteIfExists(dir);
                        return java.nio.file.FileVisitResult.CONTINUE;
                    }
                });
            }

            copyDirectory(workspaceJavaParserBase, javaparserBaseTarget);
            log.info("Copied javaparser-base-template (with generated transformations) from workspace {} to {}",
                    workspaceJavaParserBase, javaparserBaseTarget);
        } else {
            log.warn("javaparser-base-template not found in workspace at {}. Skipping copy.", workspaceJavaParserBase);
        }
    }
}
