package github.chains.core.agent.model;

import se.kth.DockerBuild;

import java.nio.file.Path;

/**
 * Request parameters for agent execution.
 * Contains all the information needed for an agent to execute its repair process.
 */
public record AgentExecutionRequest(
        /**
         * The DockerBuild instance to use for container operations
         */
        DockerBuild dockerBuild,
        
        /**
         * The project directory to analyze/repair
         */
        Path projectDir,
        
        /**
         * The name of the project
         */
        String projectName,
        
        /**
         * The Docker image name for the agent container
         */
        String dockerImageName,
        
        /**
         * Optional M2 folder to mount (may be null)
         */
        Path m2Folder,
        
        /**
         * Base directory for output files
         */
        Path outputBaseDir,
        
        /**
         * Directory where commit reports should be saved (may be null)
         */
        Path commitReportDir,
        
        /**
         * Whether to enable verbose logging
         */
        boolean verbose
) {
}

