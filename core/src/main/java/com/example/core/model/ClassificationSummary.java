package com.example.core.model;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Represents the classification outcome for a breaking update record,
 * including both the dataset-provided category and the category inferred
 * from the project log.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ClassificationSummary(
        String project,
        String breakingCommit,
        String datasetCategory,
        String inferredCategory,
            String logFile,
            String classifierReport,
            String dockerImage
) {
}

