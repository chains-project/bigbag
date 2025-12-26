package com.example.core.pipeline;

import com.example.core.config.EnvConfig;
import com.example.core.model.BreakingUpdateRecord;
import com.example.core.model.ClassificationSummary;
import com.example.core.service.ChangeImpactReportService;
import com.example.core.util.ProjectPaths;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.kth.DockerBuild;
import se.kth.models.Attempt;

import java.nio.file.Path;
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

    public AgentRepairPipeline(DockerBuild dockerBuild, EnvConfig envConfig, boolean verbose) {
        this.dockerBuild = dockerBuild;
        this.envConfig = envConfig;
        this.verbose = verbose;
        this.changeImpactService = new ChangeImpactReportService(verbose, envConfig);
    }

    @Override
    public List<Attempt> runRepairLoop(Path extractedPath, String projectName, String dockerImage,
            BreakingUpdateRecord record, Path commitReportDir, ClassificationSummary summary, Path outputBaseDir,
            Path initialLogFile) {

        log.info("Starting agent-based repair pipeline for {} (commit: {})",
                projectName, record.breakingCommit());

        Path projectDir = ProjectPaths.resolveProjectDir(extractedPath, projectName);
        String containerProjectPath = FailureCategoryUtils.normalizeContainerProjectPath(projectName);

        String agentImage = envConfig.get("AGENT_NAME").orElse("null");

        Path dockerfileDir = Path.of("images/" + agentImage + "/Dockerfile");

        String dockerImageAgentName = agentImage + ":latest";

        try {
            log.info("Ensuring agent image exists");
            dockerBuild.ensureImageExistsOrBuildFromDockerfile(dockerImageAgentName, dockerfileDir);
            log.info("Docker image {} is ready", agentImage);

        } catch (Exception e) {
            log.error("Error ensuring agent image exists", e);
        }

        String baseBranch = envConfig.get("BASE_BRANCH").orElse("main");
        String previousBranch = baseBranch; // Start from base branch

        // Find and prepare m2 folder for mounting
        // The m2 folder is typically at extractedPath/m2 (where extractedPath contains both project and m2)
        // findM2Folder looks for m2 in the parent directory of projectDir
        Path m2Folder = dockerBuild.findM2Folder(projectDir);
        if (m2Folder != null) {
            log.info("Found m2 folder at: {}. It will be mounted to /root/.m2 in container", m2Folder);
        } else {
            log.info("M2 folder not found at {}. Maven will use default repository.", extractedPath.resolve("m2"));
        }

        // Execute 'mvn compile' in the agent container
        // Similar to: gemini --yolo "execute 'mvn compile'"
        // Mount project in /workspace/{projectName} so the project is in a subfolder
        String containerWorkDir = "/workspace/" + projectName; // Working directory in the agent container
        String mavenCommand = "mvn compile";

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

        log.info("Executing '{}' in agent container {} (project: {}, workDir: {}, env vars: {}, m2: {})",
                mavenCommand, dockerImageAgentName, projectName, containerWorkDir, envVars.size(),
                m2Folder != null ? "mounted" : "not mounted");

        boolean compileSuccess = dockerBuild.executeMavenCommandInContainer(
                dockerImageAgentName,
                projectDir,
                containerWorkDir,
                mavenCommand,
                compileLogFile,
                envVars.isEmpty() ? null : envVars, // Pass null if no env vars, or the map
                m2Folder // Pass m2 folder path for mounting
        );

        log.info("Maven compile execution completed. Success: {}. Log saved to: {}",
                compileSuccess, compileLogFile);

        // Return empty list - this is a stub implementation
        return new ArrayList<>();
    }
}
