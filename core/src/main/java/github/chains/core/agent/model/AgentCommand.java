package github.chains.core.agent.model;

/**
 * Represents a command to be executed by an agent in the container.
 * Contains the command string and metadata about how it should be executed.
 */
public record AgentCommand(
        /**
         * The command string to execute in the container
         */
        String command,
        
        /**
         * Working directory in the container where the command should be executed
         */
        String workingDirectory,
        
        /**
         * Description of what this command does (for logging)
         */
        String description
) {
    public AgentCommand {
        if (command == null || command.isBlank()) {
            throw new IllegalArgumentException("Command cannot be null or blank");
        }
        if (workingDirectory == null || workingDirectory.isBlank()) {
            throw new IllegalArgumentException("Working directory cannot be null or blank");
        }
    }
    
    /**
     * Creates a command with default working directory "/workspace".
     */
    public static AgentCommand of(String command, String description) {
        return new AgentCommand(command, "/workspace", description);
    }
}

