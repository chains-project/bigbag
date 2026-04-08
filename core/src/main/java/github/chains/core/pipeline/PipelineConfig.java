package github.chains.core.pipeline;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Configuration class for the breaking update pipeline.
 * Holds all configuration constants and default values.
 */
public class PipelineConfig {

    // Default configuration values
    public static final int DEFAULT_MAX_ATTEMPTS = 1;
    public static final int DEFAULT_THREAD_POOL_SIZE = 4;
    public static final String DEFAULT_PROJECTS_PATH = "./projects";
    public static final String DEFAULT_OUTPUT_PATH = "./output";
    public static final String DEFAULT_RESULTS_FILE = "results.json";

    private final Path projectsPath;
    private final Path outputPath;
    private final Path resultsFilePath;
    private final int maxAttempts;
    private final int threadPoolSize;
    private final boolean forceReextraction;

    public PipelineConfig(Path projectsPath, Path outputPath, int maxAttempts, 
                          int threadPoolSize, boolean forceReextraction) {
        this.projectsPath = projectsPath != null ? projectsPath : Paths.get(DEFAULT_PROJECTS_PATH);
        this.outputPath = outputPath != null ? outputPath : Paths.get(DEFAULT_OUTPUT_PATH);
        this.maxAttempts = maxAttempts > 0 ? maxAttempts : DEFAULT_MAX_ATTEMPTS;
        this.threadPoolSize = threadPoolSize > 0 ? threadPoolSize : DEFAULT_THREAD_POOL_SIZE;
        this.forceReextraction = forceReextraction;
        this.resultsFilePath = this.outputPath.resolve(DEFAULT_RESULTS_FILE);
    }

    public PipelineConfig(Path projectsPath, Path outputPath) {
        this(projectsPath, outputPath, DEFAULT_MAX_ATTEMPTS, DEFAULT_THREAD_POOL_SIZE, false);
    }

    public Path getProjectsPath() {
        return projectsPath;
    }

    public Path getOutputPath() {
        return outputPath;
    }

    public Path getResultsFilePath() {
        return resultsFilePath;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public int getThreadPoolSize() {
        return threadPoolSize;
    }

    public boolean isForceReextraction() {
        return forceReextraction;
    }
}

