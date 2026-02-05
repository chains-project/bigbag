package github.chains.breakingclassifier;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ErrorReportAggregator {

    private ErrorReportAggregator() {
    }

    /**
     * Aggregates errors and determines the failure category from the log.
     * The category is always extracted from the log content using FailureCategorizer,
     * regardless of whether there are extracted errors or not.
     *
     * @param originalFailurePath path to the log file
     * @param errors list of extracted errors (may be empty)
     * @return BreakingReport with categorized failure type
     */
    public static BreakingReport aggregate(Path originalFailurePath, List<BreakingError> errors) {
        String failurePath = originalFailurePath != null ? originalFailurePath.toString() : "";

        // Always categorize the log directly, regardless of whether there are errors
        FailureCategory logCategory = categorizeLog(originalFailurePath);

        if (errors == null || errors.isEmpty()) {
            // No errors extracted, use the category from log analysis
            return new BreakingReport(failurePath, logCategory, List.of());
        }

        // There are errors - prefer category from errors if available, otherwise use log category
        FailureCategory errorCategory = errors.stream()
                .map(BreakingError::failureCategory)
                .filter(cat -> cat != FailureCategory.UNKNOWN)
                .findFirst()
                .orElse(FailureCategory.UNKNOWN);

        // Use error category if it's more specific than UNKNOWN, otherwise use log category
        FailureCategory reportCategory = (errorCategory != FailureCategory.UNKNOWN) 
                ? errorCategory 
                : logCategory;

        Map<String, List<BreakingError>> grouped = new LinkedHashMap<>();
        for (BreakingError error : errors) {
            grouped.computeIfAbsent(error.filePath(), key -> new ArrayList<>()).add(error);
        }

        List<FileErrorGroup> reports = new ArrayList<>();
        for (Map.Entry<String, List<BreakingError>> entry : grouped.entrySet()) {
            List<ErrorDetail> details = entry.getValue().stream()
                    .map(error -> new ErrorDetail(
                            error.lineNumber(),
                            error.columnNumber(),
                            error.message(),
                            error.details()))
                    .toList();

            reports.add(new FileErrorGroup(entry.getKey(), details));
        }
        return new BreakingReport(failurePath, reportCategory, List.copyOf(reports));
    }

    /**
     * Categorizes the log file directly by reading it and applying FailureCategorizer patterns.
     * This ensures we can always determine the category from the log, even when no errors are extracted.
     *
     * @param logPath path to the log file
     * @return the detected FailureCategory, or UNKNOWN if categorization fails
     */
    public static FailureCategory categorizeLog(Path logPath) {
        if (logPath == null) {
            return FailureCategory.UNKNOWN;
        }

        try {
            if (!Files.exists(logPath) || !Files.isRegularFile(logPath)) {
                return FailureCategory.UNKNOWN;
            }

            // Read all lines from the log
            List<String> lines = Files.readAllLines(logPath);
            if (lines.isEmpty()) {
                return FailureCategory.UNKNOWN;
            }

            // Use FailureCategorizer to determine category from log content
            return FailureCategorizer.categorize(lines);
        } catch (IOException e) {
            // If we can't read the log, return UNKNOWN
            return FailureCategory.UNKNOWN;
        }
    }
}

