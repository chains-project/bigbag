package github.chains.core.agent.impl;

import github.chains.core.agent.BaseAgent;
import github.chains.core.agent.model.AgentExecutionRequest;
import github.chains.core.agent.model.AgentExecutionResult;
import github.chains.core.config.EnvConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * OpenCode-based agent implementation supporting multiple rule generators.
 *
 * Supported rule generators (controlled by RULE_GENERATOR env var):
 * - "spoon" → uses Spoon AST transformation
 * - "javaparser" → uses JavaParser AST transformation
 *
 * OpenCode CLI syntax: {@code opencode --model <model> -p "<prompt>"}
 * API keys are injected per {@code LLM_PROVIDER} (see
 * {@link #prepareEnvironmentVariables()}).
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

    /**
     * Env value for Copilot; OpenCode CLI expects provider id
     * {@code github-copilot}.
     */
    private static final String PROVIDER_COPILOT = "copilot";
    private static final String PROVIDER_GITHUB_COPILOT = "github-copilot";

    /**
     * Google Gemini via OpenCode uses provider id {@code google} (e.g.
     * {@code google/gemini-2.5-pro}).
     */
    private static final String PROVIDER_GOOGLE = "google";
    /** Alias for {@value #PROVIDER_GOOGLE} in {@code LLM_PROVIDER} only. */
    private static final String PROVIDER_GEMINI = "gemini";

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
        this.baseFolder = ruleGenerator + "-base-template";
        this.apiDocsFolder = ruleGenerator + "-api-docs";
    }

    /**
     * Returns the Docker image name. When LLM_PROVIDER=copilot the dedicated
     * {@code opencode-copilot} image is used (pre-baked with Copilot auth files).
     */
    @Override
    public String getDockerImageName() {
        String provider = getEnv("LLM_PROVIDER").orElse("anthropic").toLowerCase();
        return isCopilotProvider(provider) ? "opencode-copilot:latest" : "opencode:latest";
    }

    /**
     * Returns the Dockerfile path matching {@link #getDockerImageName()}.
     */
    @Override
    public Path getDockerfilePath() {
        String provider = getEnv("LLM_PROVIDER").orElse("anthropic").toLowerCase();
        String imageDir = isCopilotProvider(provider) ? "opencode-copilot" : "opencode";
        return Path.of("images/" + imageDir + "/Dockerfile");
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
        Path apiDocsPath = resolveApiDocsPath();

        // Step 1b: Copy API spec and Javadoc into workspace so the agent can read them
        copyRoseauArtifactsToWorkspace(request.apiSpecPath(), request.javadocPath(), workspaceDir);

        // Step 2: Prepare environment variables
        Map<String, String> envVars = prepareEnvironmentVariables();

        // Step 3: Create a temp log file for DockerBuild to capture container stdout.
        // AgentRepairPipeline copies it to commitReportDir/agent_compile_output.log,
        // then it is deleted.
        Path compileLogFile;
        try {
            compileLogFile = Files.createTempFile("opencode-agent-compile-", ".log");
        } catch (IOException e) {
            log.warn("Could not create temp compile log, falling back to outputBaseDir: {}", e.getMessage());
            compileLogFile = request.outputBaseDir().resolve("maven_compile_output.log");
        }

        // Step 4: Build and execute the main agent command
        String apiSpecFileName = request.apiSpecPath() != null
                ? request.apiSpecPath().getFileName().toString()
                : null;
        String javadocDirName = request.javadocPath() != null
                ? request.javadocPath().getFileName().toString()
                : null;
        String containerApiSpecPath = (apiSpecFileName != null && Files.exists(workspaceDir.resolve(apiSpecFileName)))
                ? CONTAINER_WORK_DIR + "/" + apiSpecFileName
                : null;
        String containerJavadocPath = javadocDirName != null
                ? CONTAINER_WORK_DIR + "/" + javadocDirName
                : null;

        String agentCommand = buildAgentCommand(request.projectName(), CONTAINER_WORK_DIR, apiDocsPath,
                containerApiSpecPath, containerJavadocPath);
        log.info("Executing OpenCode+{} command in container {}", ruleGeneratorName, request.dockerImageName());

        // For Copilot: mount the host's gh config directory so the container can
        // authenticate.
        // Set GH_CONFIG_DIR in .env to the path of ~/.config/gh on the host where the
        // pipeline runs.
        // The host must have run "gh auth login" with Copilot scope at least once.
        String rawProvider = getEnv("LLM_PROVIDER").orElse("anthropic").toLowerCase();
        Path ghConfigDir = isCopilotProvider(rawProvider)
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
                request.javadocPath(),
                request.verbose());

        String containerId = request.dockerBuild().getLastContainerId();
        log.info("Agent command completed. Success: {}. Container: {}. Log: {}", compileSuccess, containerId,
                compileLogFile);

        // Step 5: Verify fix with mvn compile
        Path testLogFile = executeTestCommand(request, workspaceDir, envVars, apiDocsPath, ghConfigDir);
        boolean testSuccess = testLogFile != null && Files.exists(testLogFile);

        // Step 6: Copy agent logs from projectDir (mounted at /workspace/{projectName})
        Path agentExecutionLog = copyAgentExecutionLog(request.projectDir(), request.commitReportDir());
        copyFileToReport(request.projectDir().resolve("agent_session.json"), request.commitReportDir());

        // Step 7: Copy workspace contents (excluding project dir and api docs) to
        // commitReportDir
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
     * Copies roseau-api-v2.md and the Javadoc directory into the workspace so the
     * agent
     * can reference them inside the container at fixed paths.
     */
    private void copyRoseauArtifactsToWorkspace(Path apiSpecPath, Path javadocPath, Path workspaceDir) {
        if (apiSpecPath != null && Files.exists(apiSpecPath)) {
            Path dest = workspaceDir.resolve(apiSpecPath.getFileName());
            try {
                Files.copy(apiSpecPath, dest, StandardCopyOption.REPLACE_EXISTING);
                log.info("Copied {} to workspace", apiSpecPath.getFileName());
            } catch (IOException e) {
                log.warn("Failed to copy {} to workspace: {}", apiSpecPath.getFileName(), e.getMessage());
            }
        }
    }

    /**
     * Copies input_change-impact.json and breaking-classifier-report.json from
     * commitReportDir
     * into the workspace root so they are available inside the container at
     * /workspace/*.
     */
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
     * Resolves the API docs path from the environment, or null if not set / not
     * found.
     */
    private Path resolveApiDocsPath() {
        return getEnvPath(API_DOCS).filter(Files::exists).orElse(null);
    }

    /**
     * Prepares container environment variables for the OpenCode CLI.
     *
     * The provider is selected via the LLM_PROVIDER env var (default: "anthropic"):
     * <ul>
     * <li>"anthropic" → injects ANTHROPIC_API_KEY from LLM_API_KEY</li>
     * <li>"openai" → injects OPENAI_API_KEY from LLM_API_KEY</li>
     * <li>"openrouter" → injects OPENROUTER_API_KEY from LLM_API_KEY</li>
     * <li>"copilot" / {@code github-copilot} → injects GITHUB_TOKEN from
     * LLM_API_KEY (GitHub Copilot)</li>
     * <li>"google" / {@code gemini} → injects GOOGLE_GENERATIVE_AI_API_KEY
     * (required by OpenCode),
     * plus GOOGLE_API_KEY and GEMINI_API_KEY for compatibility, from
     * {@code LLM_API_KEY},
     * {@code GOOGLE_GENERATIVE_AI_API_KEY}, {@code GOOGLE_API_KEY}, or
     * {@code GEMINI_API_KEY}
     * (first set wins)</li>
     * </ul>
     */
    private Map<String, String> prepareEnvironmentVariables() {
        Map<String, String> envVars = new HashMap<>();

        String provider = getEnv("LLM_PROVIDER").orElse("anthropic").toLowerCase();

        resolveApiKeyForProvider(provider).ifPresent(key -> {
            switch (provider) {
                case PROVIDER_COPILOT:
                case PROVIDER_GITHUB_COPILOT:
                    // Inject GITHUB_TOKEN from LLM_API_KEY so the gh CLI inside the container
                    // can authenticate without needing ~/.config/gh mounted.
                    // On macOS, gh auth login stores the token in the Keychain (not in the config
                    // file), so mounting GH_CONFIG_DIR would be empty. Injecting GITHUB_TOKEN
                    // directly avoids that issue.
                    envVars.put("GITHUB_TOKEN", key);
                    log.info("Copilot provider: GITHUB_TOKEN injected from LLM_API_KEY");
                    break;
                case PROVIDER_GOOGLE:
                case PROVIDER_GEMINI:
                    // OpenCode's Google provider reads GOOGLE_GENERATIVE_AI_API_KEY (Vercel AI SDK
                    // naming).
                    envVars.put("GOOGLE_GENERATIVE_AI_API_KEY", key);
                    envVars.put("GOOGLE_API_KEY", key);
                    envVars.put("GEMINI_API_KEY", key);
                    log.info(
                            "Google provider: GOOGLE_GENERATIVE_AI_API_KEY (and GOOGLE_API_KEY, GEMINI_API_KEY) injected");
                    break;
                case "openai":
                    envVars.put("OPENAI_API_KEY", key);
                    log.info("Using OpenAI provider (OPENAI_API_KEY injected)");
                    break;
                case "openrouter":
                    envVars.put("OPENROUTER_API_KEY", key);
                    log.info("Using OpenRouter provider (OPENROUTER_API_KEY injected)");
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
     * Resolves the API key for the container. Google accepts LLM_API_KEY,
     * GOOGLE_GENERATIVE_AI_API_KEY, GOOGLE_API_KEY, or GEMINI_API_KEY; other
     * providers use LLM_API_KEY only.
     */
    private Optional<String> resolveApiKeyForProvider(String provider) {
        if (isGoogleProvider(provider)) {
            return getEnv("LLM_API_KEY")
                    .or(() -> getEnv("GOOGLE_GENERATIVE_AI_API_KEY"))
                    .or(() -> getEnv("GOOGLE_API_KEY"))
                    .or(() -> getEnv("GEMINI_API_KEY"));
        }
        return getEnv("LLM_API_KEY");
    }

    /**
     * Builds the OpenCode CLI command with a prompt tailored to the active rule
     * generator.
     * Syntax:
     * {@code opencode run -m <provider/model> --format json "<prompt>" 2>&1 | tee <log>}
     *
     * @param apiDocsPath      host path to API docs (null if not configured)
     * @param hasAnalysisFiles true if input_change-impact.json and
     *                         breaking-classifier-report.json
     *                         were copied into the workspace and are readable by
     *                         the agent
     */
    private String buildAgentCommand(String projectName, String workspaceDir, Path apiDocsPath,
            String containerApiSpecPath, String containerJavadocPath) {
        String baseFullPath = workspaceDir + "/" + baseFolder;
        // Only reference docs in the prompt if the directory is actually mounted
        String docsPath = apiDocsPath != null ? workspaceDir + "/" + apiDocsFolder : null;
        String rawProviderForModel = getEnv("LLM_PROVIDER").orElse("anthropic").toLowerCase();
        String defaultModel = defaultOpenCodeModel(rawProviderForModel);
        String model = getEnv("LLM_MODEL").orElse(defaultModel);

        String toolName = GENERATOR_SPOON.equals(ruleGeneratorName) ? "Spoon" : "JavaParser";

        // OpenCode CLI syntax: opencode run -m <provider/model> "<prompt>"
        // Provider IDs as recognized by OpenCode (use: opencode providers to list
        // them):
        // "anthropic" → anthropic/claude-sonnet-4-5
        // "openai" → openai/gpt-4o
        // "copilot" → github-copilot/gpt-4o (OpenCode uses "github-copilot", not
        // "copilot")
        // "google" / "gemini" → google/<model> (requires GOOGLE_GENERATIVE_AI_API_KEY
        // in container for OpenCode)
        //
        // IMPORTANT: GitHub Copilot API does NOT accept Personal Access Tokens (PAT).
        // It requires an OAuth token obtained via: gh auth login
        // On your local machine run: gh auth token → use that value as LLM_API_KEY
        String rawProvider = getEnv("LLM_PROVIDER").orElse("anthropic").toLowerCase();
        String openCodeProvider = toOpenCodeProviderId(rawProvider);
        String providerModel = openCodeProvider + "/" + model;

        // Run opencode and capture all output (stdout + stderr) to the log file.
        // SESSION_ID is extracted by parsing the JSON events in the log (field:
        // "sessionID":"ses_...").
        // Use /tmp for the tee target to guarantee write access regardless of how
        // volumes are mounted.
        // The project directory (/workspace/{project}/) may be read-only or may not
        // exist at command
        // start time, which would silently break the tee and prevent session ID
        // extraction.
        String logFile = "/tmp/agent_execution.log";
        String projectLogFile = workspaceDir + "/" + projectName + "/agent_execution.log";
        String sessionFile = workspaceDir + "/" + projectName + "/agent_session.json";

        String prompt = """
                Project @%s/ does not compile due to a breaking dependency update. \
                Your goal is to generate a GENERIC, REUSABLE transformation rule - not a one-off patch - \
                that can be applied to ANY Maven project affected by the same breaking change. \
                Plan: \
                1) Identify the compilation errors in @%s/ and determine which API \
                   (class/method/constructor/signature) changed in the dependency. \
                   The new dependency API specification is available at %s and the Javadoc at %s/ \
                   for reference when identifying replacement types, methods, or fields. \
                2) Characterize the breaking change abstractly: \
                   - What was the old API pattern? (e.g., Foo.bar(String)) \
                   - What is the new API pattern? (e.g., Foo.bar(String, boolean)) \
                   - What structural transformation does this require at the call site? \
                   Do NOT assume the fix is specific to this project - any project calling the old API will need the same transformation. \
                3) Generate a GENERIC %s transformation using the template in @%s/: \
                   - Match the old API pattern structurally, not by project-specific class names. \
                   - Use patterns to traverse all files and apply the fix wherever the old pattern appears. \
                   - Parameterize by fully-qualified type names and method signatures from the dependency, NOT from the client. \
                   - Save the transformation in %s/src/main/java/github/chains/Main.java. \
                   - Use the %s API documentation located in folder %s/ for reference. \
                4) Compile and validatethe transformation to ensure it has no errors. \
                5) Execute the transformation to @%s/ and ensure both compilation errors and test errors are fixed. \
                6) Ensure generalizability: ensure the generated rule contains no hardcoded project-specific identifiers. \
                   The rule must be applicable to other projects by simply changing the input source directory path.\
                """
                .formatted(
                        projectName,
                        projectName,
                        containerApiSpecPath,
                        containerJavadocPath,
                        toolName,
                        baseFullPath,
                        baseFullPath,
                        toolName,
                        docsPath,
                        projectName);

        // When the model ID contains a provider-specific routing suffix (e.g. "qwen/qwen3-coder:deepinfra/turbo"),
        // OpenCode does not recognise it from its built-in list. We write a minimal opencode.json into a
        // temporary directory and pass --dir so OpenCode picks it up as its working-directory config.
        String opencodeRunPrefix;
        if (model.contains(":")) {
            // OpenCode prepends the organization prefix (first path segment of the model id)
            // automatically when building the OpenRouter request. To avoid doubling (e.g.
            // "qwen/qwen/qwen3-coder-next:ionstream"), the `id` field in opencode.json must
            // NOT include the org prefix — OpenCode adds it. Strip "org/" from the full id.
            // e.g. "qwen/qwen3-coder-next:ionstream" → id "qwen3-coder-next:ionstream"
            String modelKey = model.replace("/", "_").replace(":", "__");
            int slashIdx = model.indexOf("/");
            String modelIdForConfig = slashIdx >= 0 ? model.substring(slashIdx + 1) : model;
            String escapedModelIdForConfig = modelIdForConfig.replace("\"", "\\\"");
            opencodeRunPrefix = String.format(
                    "mkdir -p /tmp/opencode-cfg && " +
                    "printf '{\"provider\":{\"openrouter\":{\"models\":{\"%%s\":{\"id\":\"%%s\"}}}}}' " +
                    "'%s' '%s' > /tmp/opencode-cfg/opencode.json && " +
                    "opencode run --dir /tmp/opencode-cfg -m openrouter/%s --format json",
                    modelKey, escapedModelIdForConfig, modelKey);
        } else {
            opencodeRunPrefix = "opencode run -m " + providerModel + " --format json";
        }

        return """
                %s "%s" 2>&1 | tee %s; \
                cp %s %s 2>/dev/null || true; \
                SESSION_ID=$(grep -o '"sessionID":"ses_[^"]*"' %s | head -1 | cut -d'"' -f4); \
                echo "Session ID: $SESSION_ID"; \
                if [ -n "$SESSION_ID" ]; then \
                  opencode export "$SESSION_ID" > %s && echo "Session exported to %s"; \
                else \
                  echo "WARNING: Could not extract session ID - skipping export"; \
                fi\
                """.formatted(
                opencodeRunPrefix,
                prompt,
                logFile,
                logFile,
                projectLogFile,
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
        String testCommand = String.format("cd %s && mvn test 2>&1 | tee mavenTest.log", projectPath);

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
                request.javadocPath(),
                request.verbose());
        try {
            Files.deleteIfExists(tempLog);
        } catch (IOException ignored) {
        }

        Path testLogFile = request.commitReportDir() != null
                ? request.commitReportDir().resolve("maven_test_output.log")
                : request.outputBaseDir().resolve("maven_test_output.log");

        try {
            // mavenTest.log is written at /workspace/{projectName}/mavenTest.log in the
            // container,
            // which maps to projectDir/mavenTest.log on the host (projectDir is mounted
            // there).
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
     * Copies workspace contents directly into commitReportDir, excluding the
     * project
     * directory (already in the benchmark) and the api-docs directory (read-only
     * reference).
     * Only the agent-generated artifacts (base template, session JSON, etc.) are
     * copied.
     */
    private void copyWorkspaceToReport(Path workspaceDir, Path commitReportDir, String projectName) {
        if (workspaceDir == null || commitReportDir == null)
            return;
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
     * Copies agent_execution.log from the project directory to the report
     * directory.
     */
    private Path copyAgentExecutionLog(Path projectDir, Path commitReportDir) {
        if (commitReportDir == null)
            return null;

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

    private static boolean isCopilotProvider(String rawProvider) {
        return PROVIDER_COPILOT.equals(rawProvider) || PROVIDER_GITHUB_COPILOT.equals(rawProvider);
    }

    private static boolean isGoogleProvider(String rawProvider) {
        return PROVIDER_GOOGLE.equals(rawProvider) || PROVIDER_GEMINI.equals(rawProvider);
    }

    private static String toOpenCodeProviderId(String rawProvider) {
        if (isCopilotProvider(rawProvider)) {
            return PROVIDER_GITHUB_COPILOT;
        }
        if (isGoogleProvider(rawProvider)) {
            return PROVIDER_GOOGLE;
        }
        return rawProvider;
    }

    private static String defaultOpenCodeModel(String rawProvider) {
        if (isCopilotProvider(rawProvider)) {
            return "gpt-4o";
        }
        if (isGoogleProvider(rawProvider)) {
            return "gemini-2.5-pro";
        }
        return "ministral-large-latest";
    }
}
