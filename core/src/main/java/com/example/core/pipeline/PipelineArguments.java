package com.example.core.pipeline;

import se.kth.models.FailureCategory;

import java.nio.file.Path;

/**
 * Record to hold parsed command line arguments for the pipeline.
 */
public record PipelineArguments(
        Path inputDir,
        Path projectsPath,
        Path outputPath,
        int maxAttempts,
        int threadPoolSize,
        boolean forceReextraction,
        FailureCategory filterCategory
) {
    public PipelineArguments {
        // Validation
        if (inputDir == null) {
            throw new IllegalArgumentException("Input directory is required");
        }
    }

    /**
     * Creates a PipelineConfig from these arguments.
     *
     * @return a PipelineConfig instance
     */
    public PipelineConfig toConfig() {
        return new PipelineConfig(projectsPath, outputPath, maxAttempts, threadPoolSize, forceReextraction);
    }
}

