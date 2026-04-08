package github.chains.core.agent.model;

import java.nio.file.Path;

/**
 * Represents a file or directory mount configuration for an agent container.
 * Defines what should be mounted or copied into the container and where.
 */
public record AgentMount(
        /**
         * Source path on the host system
         */
        Path sourcePath,
        
        /**
         * Destination path inside the container
         */
        String containerPath,
        
        /**
         * Whether this mount should be read-only (true) or read-write (false)
         */
        boolean readOnly,
        
        /**
         * Whether this should be copied (true) or mounted as volume (false)
         */
        boolean shouldCopy
) {
    /**
     * Creates a read-write mount (volume).
     */
    public static AgentMount mount(Path sourcePath, String containerPath) {
        return new AgentMount(sourcePath, containerPath, false, false);
    }
    
    /**
     * Creates a read-only mount (volume).
     */
    public static AgentMount mountReadOnly(Path sourcePath, String containerPath) {
        return new AgentMount(sourcePath, containerPath, true, false);
    }
    
    /**
     * Creates a copy operation (file/directory will be copied to container).
     */
    public static AgentMount copy(Path sourcePath, String containerPath) {
        return new AgentMount(sourcePath, containerPath, false, true);
    }
}

