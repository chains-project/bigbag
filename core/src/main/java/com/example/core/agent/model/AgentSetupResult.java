package com.example.core.agent.model;

import java.nio.file.Path;
import java.util.List;

/**
 * Result of agent setup operation, containing workspace directory and mount configurations.
 */
public record AgentSetupResult(
        /**
         * The workspace directory created for this agent execution
         */
        Path workspaceDir,
        
        /**
         * List of mounts that should be configured for the container
         */
        List<AgentMount> mounts,
        
        /**
         * List of files/directories that were copied to the workspace
         */
        List<Path> copiedResources
) {
}

