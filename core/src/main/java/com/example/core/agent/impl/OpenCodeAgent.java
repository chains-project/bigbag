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
 * OpenCode-based agent implementation supporting multiple rule generators.
 *
 * Supported rule generators (controlled by RULE_GENERATOR env var):
 * - "spoon"      → uses Spoon AST transformation
 * - "javaparser" → uses JavaParser AST transformation
 *
 * OpenCode CLI syntax: {@code opencode --model <model> -p "<prompt>"}
 * API key env vars injected: ANTHROPIC_API_KEY, OPENAI_API_KEY (from LLM_API_KEY)
 */
public class OpenCodeAgent extends BaseAgent {

    private static final Logger log = LoggerFactory.getLogger(OpenCodeAgent.class);

    // Supported rule generators
    public static final String GENERATOR_SPOON = "spoon";
    public static final String GENERATOR_JAVAPARSER = "javaparser";

    // Environment variable names
    private static final String BASE_TEMPLATE = "BASE_TEMPLATE";
    private static final String API_DOCS = "API_DOCS";
    private static final String CONTAINER_WORK_DIR = "/workspace";

    // Per-engine configuration, resolved at construction time
    private final String baseFolder;
    private final String apiDocsFolder;

    /**
     * Creates an OpenCodeAgent for the given rule generator.
     *
     * @param envConfig     environment configuration
     * @param llmAgentName  name of the LLM CLI binary (e.g., "opencode")
     * @param ruleGenerator rule generator to use: "spoon" or "javaparser"
     */
    public OpenCodeAgent(EnvConfig envConfig, String llmAgentName, String ruleGenerator) {
        super(envConfig, llmAgentName, ruleGenerator);
        this.baseFolder    = ruleGenerator + "-base-template";
        this.apiDocsFolder = ruleGenerator + "-api-docs";
    }

    @Override
    public void validateEnvironment() throws IllegalStateException {
        getEnvPath(BASE_TEMPLATE).ifPresentOrElse(
                path -> {
                    if (!Files.exists(path)) {
                        log.warn("BASE_TEMPLATE path does not exist: {}", path);
                    }
                },
                () -> log.warn("BASE_TEMPLATE not set. OpenCode {} agent may not function correctly.",
                        ruleGeneratorName));

        getEnvPath(API_DOCS).ifPresentOrElse(
                path -> {
                    if (!Files.exists(path)) {
                        log.warn("API_DOCS path does not exist: {}", path);
                    }
                },
                () -> log.debug("API_DOCS not set. OpenCode {} agent will run without API documentation.",
                        ruleGeneratorName));
    }

    @Override
    public AgentExecutionResult execute(AgentExecutionRequest request) throws IOException {
        log.info("Executing OpenCode+{} agent for project: {}", ruleGeneratorName, request.projectName());

        // Step 1: Setup workspace and copy base template
        Path workspaceDir = setupWorkspace(request);
        Path apiDocsPath  = resolveApiDocsPath();

        // Step 2: Prepare environment variables
        Map<String, String> envVars = prepareEnvironmentVariables();

        // Step 3: Create a temp log file for DockerBuild to capture container stdout.
        // AgentRepairPipeline copies it to commitReportDir/agent_compile_output.log, then it is deleted.
        Path compileLogFile;
        try {
            compileLogFile = Files.createTempFile("opencode-agent-compile-", ".log");
        } catch (IOException e) {
            log.warn("Could not create temp compile log, falling back to outputBaseDir: {}", e.getMessage());
            compileLogFile = request.outputBaseDir().resolve("maven_compile_output.log");
        }

        // Step 4: Build and execute the main agent command
        String agentCommand = buildAgentCommand(request.projectName(), CONTAINER_WORK_DIR);
        log.info("Executing OpenCode+{} command in container {}", ruleGeneratorName, request.dockerImageName());

        // For Copilot: mount the host's gh config directory so the container can authenticate.
        // Set GH_CONFIG_DIR in .env to the path of ~/.config/gh on the host where the pipeline runs.
        // The host must have run "gh auth login" with Copilot scope at least once.
        String rawProvider = getEnv("LLM_PROVIDER").orElse("anthropic").toLowerCase();
        Path ghConfigDir = rawProvider.equals("copilot")
                ? getEnvPath("GH_CONFIG_DIR").orElse(null)
                : null;

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
                apiDocsPath,
                apiDocsFolder,
                ghConfigDir,
                request.verbose());

        String containerId = request.dockerBuild().getLastContainerId();
        log.info("Agent command completed. Success: {}. Container: {}. Log: {}", compileSuccess, containerId, compileLogFile);

        // Step 5: Verify fix with mvn compile
        Path testLogFile  = executeTestCommand(request, workspaceDir, envVars, apiDocsPath, ghConfigDir);
        boolean testSuccess = testLogFile != null && Files.exists(testLogFile);

        // Step 6: Copy agent logs from projectDir (mounted at /workspace/{projectName})
        Path agentExecutionLog = copyAgentExecutionLog(request.projectDir(), request.commitReportDir());
        copyFileToReport(request.projectDir().resolve("agent_session.json"), request.commitReportDir());

        // Step 7: Copy workspace contents (excluding project dir and api docs) to commitReportDir
        copyWorkspaceToReport(workspaceDir, request.commitReportDir(), request.projectName());

        return new AgentExecutionResult(
                compileSuccess && testSuccess,
                workspaceDir,
                compileLogFile,
                testLogFile,
                agentExecutionLog,
                compileSuccess,
                testSuccess,
                containerId);
    }

    /**
     * Creates a temp workspace directory and copies the base template into it.
     */
    private Path setupWorkspace(AgentExecutionRequest request) throws IOException {
        log.info("Setting up OpenCode+{} workspace for project: {}", ruleGeneratorName, request.projectName());
        Path workspaceDir = createWorkspaceDirectory();

        getEnvPath(BASE_TEMPLATE).ifPresent(templatePath -> {
            if (Files.exists(templatePath)) {
                Path target = workspaceDir.resolve(baseFolder);
                try {
                    copyDirectory(templatePath, target);
                    log.info("Copied {} base template from {} to {}", ruleGeneratorName, templatePath, target);
                } catch (IOException e) {
                    log.warn("Failed to copy {} base template: {}", ruleGeneratorName, e.getMessage());
                }
            } else {
                log.warn("{} base template path does not exist: {}", ruleGeneratorName, templatePath);
            }
        });

        return workspaceDir;
    }

    /**
     * Resolves the API docs path from the environment, or null if not set / not found.
     */
    private Path resolveApiDocsPath() {
        return getEnvPath(API_DOCS).filter(Files::exists).orElse(null);
    }

    /**
     * Prepares container environment variables for the OpenCode CLI.
     *
     * The provider is selected via the LLM_PROVIDER env var (default: "anthropic"):
     * <ul>
     *   <li>"anthropic" → injects ANTHROPIC_API_KEY from LLM_API_KEY</li>
     *   <li>"openai"    → injects OPENAI_API_KEY from LLM_API_KEY</li>
     *   <li>"copilot"   → injects GITHUB_TOKEN from LLM_API_KEY (GitHub Copilot)</li>
     * </ul>
     */
    private Map<String, String> prepareEnvironmentVariables() {
        Map<String, String> envVars = new HashMap<>();

        String provider = getEnv("LLM_PROVIDER").orElse("anthropic").toLowerCase();

        getEnv("LLM_API_KEY").ifPresent(key -> {
            switch (provider) {
                case "copilot":
                    // Do NOT inject GITHUB_TOKEN as env var — OpenCode must exchange the OAuth token
                    // for a Copilot token via api.github.com/copilot_internal/v2/token.
                    // This exchange only happens when OpenCode reads credentials from ~/.config/gh,
                    // which is mounted into the container via GH_CONFIG_DIR.
                    // Re-store the token in the file (not macOS keyring) by running:
                    //   echo "<gho_token>" | gh auth login --with-token --hostname github.com
                    log.info("Copilot provider: credentials will be read from mounted ~/.config/gh");
                    break;
                case "openai":
                    envVars.put("OPENAI_API_KEY", key);
                    log.info("Using OpenAI provider (OPENAI_API_KEY injected)");
                    break;
                case "anthropic":
                default:
                    envVars.put("ANTHROPIC_API_KEY", key);
                    log.info("Using Anthropic provider (ANTHROPIC_API_KEY injected)");
                    break;
            }
        });

        getEnv("LLM_MODEL").ifPresent(model -> envVars.put("LLM_MODEL", model));
        return envVars;
    }

    /**
     * Builds the OpenCode CLI command with a prompt tailored to the active rule generator.
     * Syntax: {@code opencode --model <model> -p "<prompt>" 2>&1 | tee <log>}
     */
    private String buildAgentCommand(String projectName, String workspaceDir) {
        String baseFullPath = workspaceDir + "/" + baseFolder;
        String docsPath     = workspaceDir + "/" + apiDocsFolder;
        String model        = getEnv("LLM_MODEL").orElse("claude-sonnet-4-5");

        String transformationTool;
        String transformationDetail;
        if (GENERATOR_SPOON.equals(ruleGeneratorName)) {
            transformationTool   = "Spoon";
            transformationDetail = "Use Spoon AST manipulation to create the transformation.";
        } else {
            transformationTool   = "JavaParser";
            transformationDetail = "Use JavaParser AST manipulation to create the transformation.";
        }

        // OpenCode CLI syntax: opencode run -m <provider/model> "<prompt>"
        // Provider IDs as recognized by OpenCode (use: opencode providers to list them):
        //   "anthropic"      → anthropic/claude-sonnet-4-5
        //   "openai"         → openai/gpt-4o
        //   "copilot"        → github-copilot/gpt-4o  (OpenCode uses "github-copilot", not "copilot")
        //
        // IMPORTANT: GitHub Copilot API does NOT accept Personal Access Tokens (PAT).
        // It requires an OAuth token obtained via: gh auth login
        // On your local machine run: gh auth token   → use that value as LLM_API_KEY
        String rawProvider = getEnv("LLM_PROVIDER").orElse("anthropic").toLowerCase();
        String openCodeProvider = rawProvider.equals("copilot") ? "github-copilot" : rawProvider;
        String providerModel = openCodeProvider + "/" + model;

        // Run opencode and capture all output (stdout + stderr) to the log file.
        // --print-logs streams internal debug logs to stderr; combining with 2>&1 captures everything.
        // SESSION_ID is extracted by parsing the JSON events in the log (field: "sessionID":"ses_...").
        // This avoids the tail -1 bug where the last line is a log message instead of the session ID.
        String logFile = workspaceDir + "/" + projectName + "/agent_execution.log";
        String sessionFile = workspaceDir + "/" + projectName + "/agent_session.json";

        return String.format(
                "opencode run -m %s --print-logs --format json "
                        + "\"Project @%s/ does not compile. Plan: "
                        + "1) Run mvn compile in the project @%s/ to get the compilation errors only. "
                        + "2) Generate a %s source code transformation to fix the errors. "
                        + "   - Use the project in folder @%s/ as the base project template. "
                        + "   - %s "
                        + "   - Save the transformation rules inside the folder @%s/, e.g., in %s/src/main/java/github/chains/Main.java. "
                        + "   - Use the %s API documentation located in folder %s/ for reference. "
                        + "3) Ensure the generated transformation compiles correctly. "
                        + "4) Apply the transformation to fix the compilation errors. "
                        + "5) Verify that the project now compiles successfully with mvn compile. "
                        + "\" 2>&1 | tee %s; "
                        + "SESSION_ID=$(grep -o '\"sessionID\":\"ses_[^\"]*\"' %s | head -1 | cut -d'\"' -f4); "
                        + "echo \"Session ID: $SESSION_ID\"; "
                        + "if [ -n \"$SESSION_ID\" ]; then "
                        + "  opencode export \"$SESSION_ID\" > %s && echo \"Session exported to %s\"; "
                        + "else "
                        + "  echo \"WARNING: Could not extract session ID — skipping export\"; "
                        + "fi",
                providerModel,
                projectName,
                projectName,
                transformationTool,
                baseFullPath,
                transformationDetail,
                baseFullPath,
                baseFullPath,
                transformationTool,
                docsPath,
                logFile,
                logFile,
                sessionFile,
                sessionFile);
    }

    /**
     * Runs mvn compile inside the container to verify the fix.
     */
    private Path executeTestCommand(AgentExecutionRequest request, Path workspaceDir,
            Map<String, String> envVars, Path apiDocsPath, Path ghConfigDir) {
        String projectPath = CONTAINER_WORK_DIR + "/" + request.projectName();
        String testCommand = String.format("cd %s && mvn compile 2>&1 | tee mavenTest.log", projectPath);

        Path tempLog = null;
        try {
            tempLog = Files.createTempFile("opencode-mvn-test-", ".log");
        } catch (IOException e) {
            log.warn("Could not create temp log file, falling back to outputBaseDir: {}", e.getMessage());
            tempLog = request.outputBaseDir().resolve("temp_maven_test.log");
        }
        request.dockerBuild().executeMavenCommandInContainerWithWorkspace(
                request.dockerImageName(),
                workspaceDir,
                request.projectDir(),
                request.projectName(),
                CONTAINER_WORK_DIR,
                testCommand,
                tempLog,
                envVars.isEmpty() ? null : envVars,
                request.m2Folder(),
                apiDocsPath,
                apiDocsFolder,
                ghConfigDir,
                request.verbose());
        try { Files.deleteIfExists(tempLog); } catch (IOException ignored) {}

        Path testLogFile = request.commitReportDir() != null
                ? request.commitReportDir().resolve("maven_test_output.log")
                : request.outputBaseDir().resolve("maven_test_output.log");

        try {
            // mavenTest.log is written at /workspace/{projectName}/mavenTest.log in the container,
            // which maps to projectDir/mavenTest.log on the host (projectDir is mounted there).
            Path source = request.projectDir().resolve("mavenTest.log");
            if (Files.exists(source)) {
                Files.createDirectories(testLogFile.getParent());
                Files.copy(source, testLogFile, StandardCopyOption.REPLACE_EXISTING);
                log.info("Copied mvn test log to {}", testLogFile);
                return testLogFile;
            } else {
                log.warn("mvn test log not found at {}.", source);
            }
        } catch (IOException e) {
            log.warn("Failed to copy mvn test log: {}", e.getMessage());
        }
        return null;
    }

    /**
     * Copies workspace contents directly into commitReportDir, excluding the project
     * directory (already in the benchmark) and the api-docs directory (read-only reference).
     * Only the agent-generated artifacts (base template, session JSON, etc.) are copied.
     */
    private void copyWorkspaceToReport(Path workspaceDir, Path commitReportDir, String projectName) {
        if (workspaceDir == null || commitReportDir == null) return;
        if (!Files.exists(workspaceDir)) {
            log.warn("Workspace directory not found, skipping workspace copy: {}", workspaceDir);
            return;
        }
        try {
            Files.createDirectories(commitReportDir);
            try (java.util.stream.Stream<Path> entries = Files.list(workspaceDir)) {
                entries.forEach(entry -> {
                    String name = entry.getFileName().toString();
                    if (name.equals(projectName) || name.equals(apiDocsFolder)) {
                        log.debug("Skipping workspace entry (excluded): {}", name);
                        return;
                    }
                    Path target = commitReportDir.resolve(name);
                    try {
                        if (Files.isDirectory(entry)) {
                            copyDirectory(entry, target);
                        } else {
                            Files.copy(entry, target, StandardCopyOption.REPLACE_EXISTING);
                        }
                        log.info("Copied workspace entry {} to {}", name, target);
                    } catch (IOException e) {
                        log.warn("Failed to copy workspace entry {}: {}", name, e.getMessage());
                    }
                });
            }
        } catch (IOException e) {
            log.warn("Failed to copy workspace to report: {}", e.getMessage());
        }
    }

    /**
     * Copies agent_execution.log from the project directory to the report directory.
     */
    private Path copyAgentExecutionLog(Path projectDir, Path commitReportDir) {
        if (commitReportDir == null) return null;

        try {
            Path source = projectDir.resolve("agent_execution.log");
            if (Files.exists(source)) {
                Path target = commitReportDir.resolve("agent_execution.log");
                Files.createDirectories(commitReportDir);
                Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
                log.info("Copied agent execution log to {}", target);
                return target;
            } else {
                log.debug("Agent execution log not found at {}", source);
            }
        } catch (IOException e) {
            log.warn("Failed to copy agent execution log: {}", e.getMessage());
        }
        return null;
    }

    /**
     * Copies a single file to commitReportDir, preserving its filename. Silently skips if not found.
     */
    private void copyFileToReport(Path source, Path commitReportDir) {
        if (commitReportDir == null || source == null) return;
        if (!Files.exists(source)) {
            log.debug("File not found, skipping copy: {}", source);
            return;
        }
        try {
            Files.createDirectories(commitReportDir);
            Path target = commitReportDir.resolve(source.getFileName());
            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
            log.info("Copied {} to {}", source.getFileName(), target);
        } catch (IOException e) {
            log.warn("Failed to copy {}: {}", source.getFileName(), e.getMessage());
        }
    }

    @Override
    public void copyResults(Path workspaceDir, Path commitReportDir) throws IOException {
        if (workspaceDir == null || commitReportDir == null) {
            log.warn("Cannot copy {} results: workspaceDir or commitReportDir is null", ruleGeneratorName);
            return;
        }

        Path sourceBase = workspaceDir.resolve(baseFolder);
        if (!Files.exists(sourceBase)) {
            log.warn("{} base template not found in workspace at {}. Skipping copy.", ruleGeneratorName, sourceBase);
            return;
        }

        Files.createDirectories(commitReportDir);
        Path targetBase = commitReportDir.resolve(baseFolder);

        // Delete existing target directory before copying
        if (Files.exists(targetBase)) {
            Files.walkFileTree(targetBase, new java.nio.file.SimpleFileVisitor<Path>() {
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

        copyDirectory(sourceBase, targetBase);
        log.info("Copied {} base template (with generated rules) from {} to {}",
                ruleGeneratorName, sourceBase, targetBase);
    }
}
