package com.example.core.agent.model;

import java.nio.file.Path;

/**
 * Result of agent execution.
 * Contains the outcome and artifacts produced by the agent.
 */
public record AgentExecutionResult(
        /**
         * Whether the agent execution was successful
         */
        boolean success,
        
        /**
         * The workspace directory that was created (for cleanup)
         */
        Path workspaceDir,
        
        /**
         * Path to the compile log file
         */
        Path compileLogFile,
        
        /**
         * Path to the test log file (may be null)
         */
        Path testLogFile,
        
        /**
         * Path to the agent execution log (may be null)
         */
        Path agentExecutionLog,
        
        /**
         * Whether compilation succeeded
         */
        boolean compileSuccess,
        
        /**
         * Whether tests succeeded
         */
        boolean testSuccess
) {
    /**
     * Creates a failed execution result.
     */
    public static AgentExecutionResult failed(Path workspaceDir) {
        return new AgentExecutionResult(
                false,
                workspaceDir,
                null,
                null,
                null,
                false,
                false
        );
    }
}

