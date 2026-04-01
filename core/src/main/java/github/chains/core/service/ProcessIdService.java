package github.chains.core.service;

import java.util.UUID;

/**
 * Service for generating unique process identifiers for repair pipeline executions.
 * Each repair process gets a unique UUID that is shared across all branches and attempts
 * within that process.
 */
public class ProcessIdService {

    /**
     * Generates a unique process identifier for this repair execution.
     * 
     * @return Full UUID string (e.g., "a1b2c3d4-e5f6-7890-abcd-ef1234567890")
     */
    public static String generateProcessId() {
        return UUID.randomUUID().toString();
    }
    
    /**
     * Generates a short process identifier (first 8 characters of UUID).
     * Useful for branch names to keep them shorter while maintaining uniqueness.
     * 
     * @return Short UUID string (e.g., "a1b2c3d4")
     */
    public static String generateShortProcessId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}

