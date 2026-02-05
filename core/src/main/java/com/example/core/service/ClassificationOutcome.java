package com.example.core.service;

import github.chains.breakingclassifier.BreakingReport;

import java.nio.file.Path;

/**
 * Holds the result of running breaking-classifier, including the log file that
 * was analyzed, the aggregated report, and the path to the JSON report emitted
 * by the CLI.
 */
public record ClassificationOutcome(
        Path logFile,
        Path reportJson,
        BreakingReport report
) {
}

