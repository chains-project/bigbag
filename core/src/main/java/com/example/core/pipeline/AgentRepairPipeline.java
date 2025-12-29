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

        // Copy original project (estado con errores) al directorio del commit (reports/{model}/{commit}/original)
        if (commitReportDir != null) {
            Path commitOriginalDir = commitReportDir.resolve("original");
            try {
                if (Files.exists(commitOriginalDir)) {
                    deleteDirectory(commitOriginalDir);
                }
                copyDirectory(projectDir, commitOriginalDir);
                log.info("Copied original project to commit report directory {}", commitOriginalDir);
            } catch (IOException e) {
                log.warn("Could not copy original project to commit report directory {}: {}", commitOriginalDir, e.getMessage());
            }
        }

        // The project is now in the agent branch created above. We mount it directly without copying.
        log.info("Project directory {} is in agent branch {} for agent modifications", projectDir, agentBranchName);

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
        String mavenCommand = String.format(
            "gemini --model gemini-3-pro-preview --debug --yolo \" 'Project /%s/ does not compile. Plan: "
          + "1) Run `mvn compile` in the project /%s/ to get the compilation errors only. "
          + "2) Generate a Spoon source code transformation to fix the errors. "
          + "   - Use the project in folder %s/ as the base project. "
          + "   - Only modify the files that are causing the compilation errors. "
          + "   - Save the transformation rules inside the project /%s/, e.g., in `src/main/java/spoon/processors/`. "
          + "   - Use the Spoon API documentation located in folder %s/ for reference. "
          + "3) Ensure the generated transformation file compiles correctly. "
          + "4) Apply the transformation to fix the compilation errors. "
          + "5) Verify that the project now compiles successfully with `mvn compile`. "
          + "> maven_agent_build.log 2>&1'\"",
            projectName, projectName, spoonBaseFolder, spoonBaseFolder, apiDocsFolder
        );
        
        
        // Create log file for the compile command execution
        // Use outputBaseDir to create an absolute path so getParent() works correctly
        Path compileLogFile = outputBaseDir.resolve("mvn-compile.log");

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

        // After compile command, run mvn test inside /workspace/{projectName} and export log to commit folder
        Path testLogFile = commitReportDir != null
                ? commitReportDir.resolve("mvn-test.log")
                : outputBaseDir.resolve("mvn-test.log");

        // For tests, use the project directory inside workspace as working dir
        String containerProjectWorkDir = containerWorkDir.endsWith("/")
                ? containerWorkDir + projectName
                : containerWorkDir + "/" + projectName;

        boolean testSuccess = dockerBuild.executeMavenCommandInContainerWithWorkspace(
                dockerImageAgentName,
                workspaceDir,
                projectDir,
                projectName,
                containerProjectWorkDir,
                "mvn test",
                testLogFile,
                envVars.isEmpty() ? null : envVars,
                m2Folder,
                spoonApiDocs,
                this.verbose
        );

        log.info("Maven test execution completed. Success: {}. Log saved to: {}", testSuccess, testLogFile);

        // Copy both logs to commit report directory with distinct names
        if (commitReportDir != null) {
            try {
                Path compileLogTarget = commitReportDir.resolve("maven_compile.log");
                Files.createDirectories(commitReportDir);
                Files.copy(compileLogFile, compileLogTarget, StandardCopyOption.REPLACE_EXISTING);
                log.info("Copied compile log to {}", compileLogTarget);
            } catch (IOException e) {
                log.warn("Failed to copy compile log to commit directory: {}", e.getMessage());
            }

            try {
                Path testLogTarget = commitReportDir.resolve("maven_test.log");
                Files.createDirectories(commitReportDir);
                Files.copy(testLogFile, testLogTarget, StandardCopyOption.REPLACE_EXISTING);
                log.info("Copied test log to {}", testLogTarget);
            } catch (IOException e) {
                log.warn("Failed to copy test log to commit directory: {}", e.getMessage());
            }
        }

        // Return empty list - this is a stub implementation
        return new ArrayList<>();
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

    private void deleteDirectory(Path target) throws IOException {
        if (target == null || !Files.exists(target)) {
            return;
        }
        Files.walkFileTree(target, new SimpleFileVisitor<Path>() {
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
}
