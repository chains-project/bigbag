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

    /**
     * Gemini-based agent implementation supporting multiple rule generators.
     *
     * Supported rule generators (controlled by RULE_GENERATOR env var):
     * - "spoon"      → uses Spoon AST transformation
     * - "javaparser" → uses JavaParser AST transformation
     *
     * Gemini CLI syntax: {@code gemini --model <model> --yolo --debug -o json -p "<prompt>"}
     * API key env vars injected: GEMINI_API_KEY, GOOGLE_API_KEY (from LLM_API_KEY)
     */
    public class GeminiAgent extends BaseAgent {

        private static final Logger log = LoggerFactory.getLogger(GeminiAgent.class);

        public static final String GENERATOR_SPOON      = "spoon";
        public static final String GENERATOR_JAVAPARSER = "javaparser";

        private static final String BASE_TEMPLATE    = "BASE_TEMPLATE";
        private static final String API_DOCS         = "API_DOCS";
        private static final String CONTAINER_WORK_DIR = "/workspace";

        private final String baseFolder;
        private final String apiDocsFolder;

        /**
         * Creates a GeminiAgent for the given rule generator.
         *
         * @param envConfig     environment configuration
         * @param llmAgentName  name of the LLM CLI binary (e.g., "gemini")
         * @param ruleGenerator rule generator to use: "spoon" or "javaparser"
         */
        public GeminiAgent(EnvConfig envConfig, String llmAgentName, String ruleGenerator) {
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
                    () -> log.warn("BASE_TEMPLATE not set. Gemini {} agent may not function correctly.",
                            ruleGeneratorName));

            getEnvPath(API_DOCS).ifPresentOrElse(
                    path -> {
                        if (!Files.exists(path)) {
                            log.warn("API_DOCS path does not exist: {}", path);
                        }
                    },
                    () -> log.debug("API_DOCS not set. Gemini {} agent will run without API documentation.",
                            ruleGeneratorName));
        }

        @Override
        public AgentExecutionResult execute(AgentExecutionRequest request) throws IOException {
            log.info("Executing Gemini+{} agent for project: {}", ruleGeneratorName, request.projectName());

            // Step 1: Setup workspace and copy base template
            Path workspaceDir = setupWorkspace(request);
            Path apiDocsPath  = resolveApiDocsPath();

            // Step 2: Prepare environment variables
            Map<String, String> envVars = prepareEnvironmentVariables();

            // Step 3: Create log file
            Path compileLogFile = request.outputBaseDir().resolve("maven_compile_output.log");

            // Step 4: Build and execute the main agent command
            String agentCommand = buildAgentCommand(request.projectName(), CONTAINER_WORK_DIR, apiDocsPath);
            log.info("Executing Gemini+{} command in container {}", ruleGeneratorName, request.dockerImageName());

            Path geminiChatExportDir = request.commitReportDir() != null
                    ? request.commitReportDir().resolve("gemini-chat-sessions")
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
                    request.verbose(),
                    geminiChatExportDir);

            log.info("Agent command completed. Success: {}. Log: {}", compileSuccess, compileLogFile);

            // Step 5: Verify fix with mvn compile
            Path testLogFile  = executeTestCommand(request, workspaceDir, envVars, apiDocsPath);
            boolean testSuccess = testLogFile != null && Files.exists(testLogFile);

            // Step 6: Copy agent logs from projectDir
            Path agentExecutionLog = copyAgentExecutionLog(request.projectDir(), request.commitReportDir());
            copyFileToReport(request.projectDir().resolve("agent_session.json"), request.commitReportDir());

            return new AgentExecutionResult(
                    compileSuccess && testSuccess,
                    workspaceDir,
                    compileLogFile,
                    testLogFile,
                    agentExecutionLog,
                    compileSuccess,
                    testSuccess,
                    null);
        }

        private Path setupWorkspace(AgentExecutionRequest request) throws IOException {
            log.info("Setting up Gemini+{} workspace for project: {}", ruleGeneratorName, request.projectName());
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

        private Path resolveApiDocsPath() {
            return getEnvPath(API_DOCS).filter(Files::exists).orElse(null);
        }

        private Map<String, String> prepareEnvironmentVariables() {
            Map<String, String> envVars = new HashMap<>();

            getEnv("LLM_API_KEY").ifPresent(key -> {
                envVars.put("GEMINI_API_KEY", key);
                envVars.put("GOOGLE_API_KEY", key);
            });

            getEnv("LLM_MODEL").ifPresent(model -> envVars.put("LLM_MODEL", model));

            return envVars;
        }

        /**
         * Human-readable tool name for prompts (depends on {@link #ruleGeneratorName}).
         */
        private String toolDisplayName() {
            return switch (ruleGeneratorName.toLowerCase()) {
                case GENERATOR_SPOON -> "Spoon";
                case GENERATOR_JAVAPARSER -> "JavaParser";
                default -> Character.toUpperCase(ruleGeneratorName.charAt(0)) + ruleGeneratorName.substring(1);
            };
        }

        /**
         * Builds the Gemini CLI command with a short plan prompt tailored to the active rule generator
         * (spoon-base-template / javaparser-base-template, etc.).
         * Syntax: {@code gemini --model <m> --yolo --debug -o json -p " '<plan> 2>&1 | tee ...agent_execution.log'"}
         */
        private String buildAgentCommand(String projectName, String workspaceDir, Path apiDocsPath) {
            String baseFullPath = workspaceDir + "/" + baseFolder;
            String docsPath = apiDocsPath != null ? workspaceDir + "/" + apiDocsFolder : null;
            String model = getEnv("LLM_MODEL").orElse("gemini-2.5-pro-preview-03-25");
            String tool = toolDisplayName();
            String mainJava = baseFullPath + "/src/main/java/github/chains/Main.java";
            String agentLog = workspaceDir + "/" + projectName + "/agent_execution.log";

            String plan = buildShortPlanPrompt(projectName, baseFullPath, docsPath, tool, mainJava);

            return String.format(
                    "%s --model %s --yolo -o json "
                            + "-p \" '%s"
                            + "2>&1 | tee %s'\"",
                    llmAgentName,
                    model,
                    plan,
                    agentLog);
        }

        /**
         * Short reusable plan; {@code tool} and paths follow the active engine (Spoon, JavaParser, …).
         */
        private String buildShortPlanPrompt(String projectName, String baseFullPath, String docsPath,
                String tool, String mainJavaPath) {
            String docsLine = docsPath != null
                    ? "   - Use the " + tool + " API documentation located in folder " + docsPath + "/ for reference. "
                    : "";
            return "Project @" + projectName + "/ does not compile due to a breaking dependency update. Your goal is to generate a GENERIC, REUSABLE transformation rule, not a one off patch, that can be applied to ANY Maven project affected by the same breaking change. " 
                    +" Plan: "
                    + "1) Run mvn test-compile in the project @" + projectName + "/ to collect compilation errors only. Identify the root cause: which API (class/method/constructor/signature) changed in the dependency? "
                    +"2) Characterize the breaking change abstractly: "
                    + "   - What was the old API pattern? (e.g., Foo.bar(String)) "
                    + "   - What is the new API pattern? (e.g., Foo.bar(String, boolean)) "
                    + "   - What structural transformation does this require at the call site? "
                    + "   - Do NOT assume the fix is specific to this project. Any project calling the old API will need the same transformation. "
                    + "3) Generate a GENERIC "+ tool + " transformation using the template in @"+baseFullPath+"/:"
                    + "   - Match the old API pattern structurally, not by project-specific class names. "
                    + "   - Parameterize by fully-qualified type names and method signatures from the dependency, NOT from the client."
                    + "   - Save the transformation in  "+ baseFullPath + "/src/main/java/github/chains/Main.java."
                    + "   - Refer to the "+ tool + " API docs at @"+ docsLine + " for correct AST node types."
                    + "4) Compile the transformation and verify it has no errors. "
                    + "5) Apply the transformation to the project @" + projectName + "/ and verify with mvn test-compile that the project now builds. "
                    + "   - VERIFICATION PHASE: Run mvn test-compile in the project @" + projectName + "/ to verify the transformation. "
                    + "6) Confirm generalizability: ensure the generated rule contains no hardcoded project-specific identifiers. "
                    + "   - The rule must be applicable to other projects by simply changing the input source directory path. ";
                    
        }

        private Path executeTestCommand(AgentExecutionRequest request, Path workspaceDir,
                Map<String, String> envVars, Path apiDocsPath) {
            String projectPath = CONTAINER_WORK_DIR + "/" + request.projectName();
            String testCommand = String.format("cd %s && mvn test-compile 2>&1 | tee mavenTest.log", projectPath);

            Path tempLog = request.outputBaseDir().resolve("temp_maven_test.log");

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
                    request.verbose());

            Path testLogFile = request.commitReportDir() != null
                    ? request.commitReportDir().resolve("maven_test_output.log")
                    : request.outputBaseDir().resolve("maven_test_output.log");

            try {
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
