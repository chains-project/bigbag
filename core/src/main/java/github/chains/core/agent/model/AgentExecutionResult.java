package github.chains.core.agent.model;

import java.nio.file.Path;

/**
 * Result of agent execution.
 * Contains the outcome and artifacts produced by the agent.
 */
public record AgentExecutionResult(
        boolean success,
        Path workspaceDir,
        Path compileLogFile,
        Path testLogFile,
        Path agentExecutionLog,
        boolean compileSuccess,
        boolean testSuccess,
        /** ID of the Docker container used for this execution (may be null). */
        String containerId
) {
    public static AgentExecutionResult failed(Path workspaceDir) {
        return new AgentExecutionResult(false, workspaceDir, null, null, null, false, false, null);
    }
}
