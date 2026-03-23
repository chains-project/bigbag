package com.example.core.agent;

import com.example.core.agent.model.AgentExecutionRequest;
import com.example.core.agent.model.AgentExecutionResult;
import com.example.core.config.EnvConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Abstract base class for all agents.
 * Defines the contract that all agent implementations must follow.
 * 
 * This class provides a generic, scalable framework for agent-based repair pipelines,
 * allowing different agents (Spoon, OpenRewrite, etc.) to be plugged in dynamically.
 * 
 * Each agent follows the same execution process but implements it according to its
 * specific needs. The pipeline only needs to call execute() and the agent handles
 * everything internally (setup, command execution, cleanup).
 */
public abstract class BaseAgent {
    
    protected static final Logger log = LoggerFactory.getLogger(BaseAgent.class);
    
    protected final EnvConfig envConfig;
    protected final String llmAgentName;  // e.g., "gemini", "copilot"
    protected final String ruleGeneratorName;  // e.g., "spoon", "openrewrite"
    
    protected BaseAgent(EnvConfig envConfig, String llmAgentName, String ruleGeneratorName) {
        if (envConfig == null) {
            throw new IllegalArgumentException("EnvConfig cannot be null");
        }
        if (llmAgentName == null || llmAgentName.isBlank()) {
            throw new IllegalArgumentException("LLM agent name cannot be null or blank");
        }
        if (ruleGeneratorName == null || ruleGeneratorName.isBlank()) {
            throw new IllegalArgumentException("Rule generator name cannot be null or blank");
        }
        this.envConfig = envConfig;
        this.llmAgentName = llmAgentName;
        this.ruleGeneratorName = ruleGeneratorName;
    }
    
    /**
     * Gets the name of this rule generator (e.g., "spoon", "openrewrite").
     * 
     * @return the rule generator name
     */
    public String getName() {
        return ruleGeneratorName;
    }
    
    /**
     * Gets the LLM agent name (e.g., "gemini", "copilot").
     * 
     * @return the LLM agent name
     */
    public String getLlmAgentName() {
        return llmAgentName;
    }
    
    /**
     * Gets the rule generator name (e.g., "spoon", "openrewrite").
     * 
     * @return the rule generator name
     */
    public String getRuleGeneratorName() {
        return ruleGeneratorName;
    }
    
    /**
     * Gets the Docker image name for this agent.
     * Default implementation returns "{llmAgentName}:latest".
     * The Docker image contains the LLM agent (gemini, copilot, etc.).
     * 
     * @return the Docker image name
     */
    public String getDockerImageName() {
        return llmAgentName +":latest";
    }
    
    /**
     * Gets the path to the Dockerfile for this agent.
     * Default implementation returns "images/{llmAgentName}/Dockerfile".
     * 
     * @return the path to the Dockerfile
     */
    public Path getDockerfilePath() {
        return Path.of("images/" + llmAgentName + "/Dockerfile");
    }
    
    /**
     * Validates that all required environment variables for this agent are present.
     * 
     * @throws IllegalStateException if any required environment variables are missing
     */
    public abstract void validateEnvironment() throws IllegalStateException;
    
    /**
     * Executes the agent's repair process.
     * 
     * This is the main entry point for agent execution. Each agent implements this method
     * to handle its specific execution flow:
     * 1. Setup workspace and mounts
     * 2. Prepare and execute commands
     * 3. Collect results and logs
     * 4. Cleanup resources
     * 
     * The pipeline calls this method and the agent handles everything internally.
     * 
     * @param request the execution request containing all necessary parameters
     * @return the execution result with success status and artifact paths
     * @throws IOException if execution fails
     */
    public abstract AgentExecutionResult execute(AgentExecutionRequest request) throws IOException;
    
    /**
     * Copies agent-specific results/output from the workspace to the commit report directory.
     * Each agent implements this to copy its specific artifacts (e.g., transformation rules,
     * generated code, configuration files, etc.).
     * 
     * Default implementation does nothing. Override to copy agent-specific results.
     * 
     * @param workspaceDir the workspace directory where results were generated
     * @param commitReportDir the target directory where results should be copied
     * @throws IOException if copying fails
     */
    public void copyResults(Path workspaceDir, Path commitReportDir) throws IOException {
        // Default: no results to copy
        log.debug("Rule generator {} has no specific results to copy", ruleGeneratorName);
    }
    
    /**
     * Helper method to create a temporary workspace directory.
     * 
     * @return the created workspace directory
     * @throws IOException if directory creation fails
     */
    protected Path createWorkspaceDirectory() throws IOException {
        Path workspaceDir = Files.createTempDirectory("agent-workspace-");
        log.info("Created workspace directory at: {}", workspaceDir);
        return workspaceDir;
    }
    
    /**
     * Helper method to copy a directory recursively.
     * 
     * @param source the source directory
     * @param target the target directory (will be created if it doesn't exist)
     * @throws IOException if copy fails
     */
    protected void copyDirectory(Path source, Path target) throws IOException {
        if (source == null || !Files.exists(source)) {
            log.warn("Source directory does not exist: {}", source);
            return;
        }
        
        Files.walkFileTree(source, new java.nio.file.SimpleFileVisitor<Path>() {
            @Override
            public java.nio.file.FileVisitResult preVisitDirectory(Path dir, java.nio.file.attribute.BasicFileAttributes attrs) throws IOException {
                Path targetDir = target.resolve(source.relativize(dir));
                Files.createDirectories(targetDir);
                return java.nio.file.FileVisitResult.CONTINUE;
            }
            
            @Override
            public java.nio.file.FileVisitResult visitFile(Path file, java.nio.file.attribute.BasicFileAttributes attrs) throws IOException {
                Path targetFile = target.resolve(source.relativize(file));
                Files.copy(file, targetFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                return java.nio.file.FileVisitResult.CONTINUE;
            }
        });
    }
    
    /**
     * Helper method to get a required environment variable.
     * 
     * @param key the environment variable key
     * @return the value
     * @throws IllegalStateException if the variable is not set
     */
    protected String requireEnv(String key) {
        return envConfig.require(key);
    }
    
    /**
     * Helper method to get an optional environment variable.
     * 
     * @param key the environment variable key
     * @return Optional containing the value if present
     */
    protected Optional<String> getEnv(String key) {
        return envConfig.get(key);
    }
    
    /**
     * Helper method to get an optional path environment variable.
     * 
     * @param key the environment variable key
     * @return Optional containing the Path if present
     */
    protected Optional<Path> getEnvPath(String key) {
        return envConfig.getPath(key);
    }
}

