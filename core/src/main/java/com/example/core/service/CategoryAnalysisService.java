package com.example.core.service;

import com.example.core.model.BreakingUpdateRecord;
import github.chains.breakingclassifier.BreakingClassifierApp;
import github.chains.breakingclassifier.BreakingReport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.kth.models.FailureCategory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Service for running category-specific analysis based on failure category.
 * Each category has its own analysis strategy.
 * 
 * This service ensures that the pipeline handles all failure categories,
 * not just COMPILATION_FAILURE.
 */
public class CategoryAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(CategoryAnalysisService.class);
    private final boolean verbose;
    private final BreakingClassifierApp classifierApp;

    public CategoryAnalysisService(boolean verbose) {
        this.verbose = verbose;
        this.classifierApp = new BreakingClassifierApp();
    }

    /**
     * Analyzes a log file based on its failure category.
     * Executes category-specific analysis for all categories, not just COMPILATION_FAILURE.
     *
     * @param category the failure category
     * @param logFile  the log file to analyze
     * @param projectDir the project directory
     * @param record   the breaking update record
     */
    public void analyzeCategory(FailureCategory category, Path logFile, Path projectDir, BreakingUpdateRecord record) {
        if (logFile == null || !Files.exists(logFile)) {
            log.warn("Log file does not exist for category analysis: {}", logFile);
            return;
        }

        log.info("Running {} analysis for log: {}", category, logFile);

        try {
            BreakingReport report = classifierApp.analyzeLog(logFile, null);
            if (report == null) {
                log.warn("Breaking-classifier returned null for log: {}", logFile);
                return;
            }

            switch (category) {
                case COMPILATION_FAILURE:
                    analyzeCompilationFailure(report, logFile, projectDir, record);
                    break;
                case TEST_FAILURE:
                    analyzeTestFailure(report, logFile, projectDir, record);
                    break;
                case JAVA_VERSION_FAILURE:
                    analyzeJavaVersionFailure(report, logFile, projectDir, record);
                    break;
                case WERROR_FAILURE:
                    analyzeWerrorFailure(report, logFile, projectDir, record);
                    break;
                case ENFORCER_FAILURE:
                    analyzeEnforcerFailure(report, logFile, projectDir, record);
                    break;
                case DEPENDENCY_RESOLUTION_FAILURE:
                    analyzeDependencyResolutionFailure(report, logFile, projectDir, record);
                    break;
                case DEPENDENCY_LOCK_FAILURE:
                    analyzeDependencyLockFailure(report, logFile, projectDir, record);
                    break;
                case BUILD_SUCCESS:
                    log.info("Build successful, no analysis needed");
                    break;
                case UNKNOWN_FAILURE:
                    analyzeUnknownFailure(report, logFile, projectDir, record);
                    break;
                default:
                    log.warn("Unknown category: {}, using generic analysis", category);
                    analyzeUnknownFailure(report, logFile, projectDir, record);
            }
        } catch (Exception e) {
            log.error("Error during category-specific analysis for {}: {}", category, logFile, e);
        }
    }

    /**
     * Analyzes compilation failures.
     * Extracts compilation errors and generates detailed reports.
     */
    private void analyzeCompilationFailure(BreakingReport report, Path logFile, Path projectDir, BreakingUpdateRecord record) {
        log.info("Analyzing COMPILATION_FAILURE");
        int totalErrors = report.errorsByFile().stream()
                .mapToInt(f -> f.errors().size())
                .sum();
        log.info("Compilation failure analysis: {} errors found in {} files", totalErrors, report.errorsByFile().size());
        if (verbose) {
            System.out.println("  Compilation errors: " + report.errorsByFile().size() + " files");
            System.out.println("  Total errors: " + totalErrors);
        }
    }

    /**
     * Analyzes test failures.
     * Extracts test failure information.
     */
    private void analyzeTestFailure(BreakingReport report, Path logFile, Path projectDir, BreakingUpdateRecord record) {
        log.info("Analyzing TEST_FAILURE");
        int totalErrors = report.errorsByFile().stream()
                .mapToInt(f -> f.errors().size())
                .sum();
        log.info("Test failure analysis: {} errors found", totalErrors);
        if (verbose) {
            System.out.println("  Test failures detected");
            System.out.println("  Files with test errors: " + report.errorsByFile().size());
        }
    }

    /**
     * Analyzes Java version failures.
     * Extracts Java version incompatibility information.
     */
    private void analyzeJavaVersionFailure(BreakingReport report, Path logFile, Path projectDir, BreakingUpdateRecord record) {
        log.info("Analyzing JAVA_VERSION_FAILURE");
        int totalErrors = report.errorsByFile().stream()
                .mapToInt(f -> f.errors().size())
                .sum();
        log.info("Java version failure analysis: {} errors found", totalErrors);
        if (verbose) {
            System.out.println("  Java version incompatibility detected");
            System.out.println("  Files affected: " + report.errorsByFile().size());
        }
    }

    /**
     * Analyzes Werror failures.
     * Extracts warning-related failure information.
     */
    private void analyzeWerrorFailure(BreakingReport report, Path logFile, Path projectDir, BreakingUpdateRecord record) {
        log.info("Analyzing WERROR_FAILURE");
        int totalErrors = report.errorsByFile().stream()
                .mapToInt(f -> f.errors().size())
                .sum();
        log.info("Werror failure analysis: {} errors found", totalErrors);
        if (verbose) {
            System.out.println("  Werror failures detected");
            System.out.println("  Files with warnings treated as errors: " + report.errorsByFile().size());
        }
    }

    /**
     * Analyzes enforcer failures.
     * Extracts Maven enforcer plugin failure information.
     */
    private void analyzeEnforcerFailure(BreakingReport report, Path logFile, Path projectDir, BreakingUpdateRecord record) {
        log.info("Analyzing ENFORCER_FAILURE");
        int totalErrors = report.errorsByFile().stream()
                .mapToInt(f -> f.errors().size())
                .sum();
        log.info("Enforcer failure analysis: {} errors found", totalErrors);
        if (verbose) {
            System.out.println("  Enforcer failures detected");
            System.out.println("  Maven enforcer plugin violations: " + report.errorsByFile().size());
        }
    }

    /**
     * Analyzes dependency resolution failures.
     * Extracts dependency resolution error information.
     */
    private void analyzeDependencyResolutionFailure(BreakingReport report, Path logFile, Path projectDir, BreakingUpdateRecord record) {
        log.info("Analyzing DEPENDENCY_RESOLUTION_FAILURE");
        int totalErrors = report.errorsByFile().stream()
                .mapToInt(f -> f.errors().size())
                .sum();
        log.info("Dependency resolution failure analysis: {} errors found", totalErrors);
        if (verbose) {
            System.out.println("  Dependency resolution failures detected");
            System.out.println("  Dependencies that could not be resolved");
        }
    }

    /**
     * Analyzes dependency lock failures.
     * Extracts dependency lock plugin failure information.
     */
    private void analyzeDependencyLockFailure(BreakingReport report, Path logFile, Path projectDir, BreakingUpdateRecord record) {
        log.info("Analyzing DEPENDENCY_LOCK_FAILURE");
        int totalErrors = report.errorsByFile().stream()
                .mapToInt(f -> f.errors().size())
                .sum();
        log.info("Dependency lock failure analysis: {} errors found", totalErrors);
        if (verbose) {
            System.out.println("  Dependency lock failures detected");
            System.out.println("  Dependency lock plugin violations");
        }
    }

    /**
     * Analyzes unknown failures.
     * Performs generic analysis when category is unknown.
     */
    private void analyzeUnknownFailure(BreakingReport report, Path logFile, Path projectDir, BreakingUpdateRecord record) {
        log.info("Analyzing UNKNOWN_FAILURE");
        int totalErrors = report.errorsByFile().stream()
                .mapToInt(f -> f.errors().size())
                .sum();
        log.info("Unknown failure analysis: {} errors found", totalErrors);
        if (verbose) {
            System.out.println("  Unknown failure type, performing generic analysis");
            System.out.println("  Files with errors: " + report.errorsByFile().size());
        }
    }
}

