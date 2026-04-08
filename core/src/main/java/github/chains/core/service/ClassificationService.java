package github.chains.core.service;

import github.chains.core.model.BreakingUpdateRecord;
import github.chains.core.pipeline.ProjectLogLocator;
import github.chains.breakingclassifier.BreakingClassifierApp;
import github.chains.breakingclassifier.BreakingReport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Service class responsible for running breaking-classifier on build logs.
 */
public class ClassificationService {

    private static final Logger log = LoggerFactory.getLogger(ClassificationService.class);

    private final boolean verbose;
    private final BreakingClassifierApp classifierApp;

    public ClassificationService(boolean verbose) {
        this.verbose = verbose;
        this.classifierApp = new BreakingClassifierApp();
    }

    /**
     * Runs only the breaking-classifier without extracting JARs (assumes JARs already exist).
     *
     * @param record        the breaking update record
     * @param extractedPath the path where the project was extracted
     * @param breakingCommit the breaking commit hash
     */
    public ClassificationOutcome runClassifierOnly(BreakingUpdateRecord record, Path extractedPath, String breakingCommit) {
        try {
            if (verbose) {
                System.out.println("  Running analysis only (JARs already exist)");
            }

            // Find log file in project folder (inside project/{project}/)
            Path projectDir = extractedPath;
            String projectName = record.project();
            Path logFile = ProjectLogLocator.findLogFile(projectDir, projectName, breakingCommit);

            if (logFile == null || !Files.exists(logFile)) {
                Path expectedPath = projectDir.resolve(projectName != null ? projectName : "").resolve(breakingCommit + ".log");
                log.warn("Log file not found for record {}: expected at {}", record.descriptor(), expectedPath);
                if (verbose) {
                    System.out.println("  ⚠ Log file not found: " + expectedPath);
                }
                return null;
            }

            return runClassifier(logFile, extractedPath);

        } catch (Exception e) {
            log.error("Error running classifier for record: {}", record.descriptor(), e);
            if (verbose) {
                System.out.println("  ✗ Error: " + e.getMessage());
                e.printStackTrace();
            }
            return null;
        }
    }

    /**
     * Runs breaking-classifier on a log file and persists the CLI JSON output next to the commit.
     *
     * @param logFile the path to the log file
     * @param commitDir directory where the project/commit was extracted
     */
    public ClassificationOutcome runClassifier(Path logFile, Path commitDir) {
        if (verbose) {
            System.out.println("  Running breaking-classifier on: " + logFile);
        }

        Path cliReportPath = commitDir.resolve("breaking-classifier-report.json");
        try {
            BreakingReport report = classifierApp.analyzeLog(logFile, cliReportPath);

            if (report != null) {
                github.chains.breakingclassifier.FailureCategory category = report.failureCategory();
                System.out.println("  Category: " + category);
                if (verbose) {
                    System.out.println("  Original failure path: " + report.originalFailurePath());
                    System.out.println("  Errors found: " + report.errorsByFile().size());
                    System.out.println("  CLI JSON: " + cliReportPath);
                }
                return new ClassificationOutcome(logFile, cliReportPath, report);
            } else {
                log.warn("Breaking-classifier returned null for log file: {}", logFile);
                if (verbose) {
                    System.out.println("  ⚠ Breaking-classifier returned null");
                }
                return null;
            }
        } catch (Exception e) {
            log.error("Error running breaking-classifier on {}", logFile, e);
            if (verbose) {
                System.out.println("  ⚠ Failed to run breaking-classifier: " + e.getMessage());
            }
            return null;
        }
    }
}

