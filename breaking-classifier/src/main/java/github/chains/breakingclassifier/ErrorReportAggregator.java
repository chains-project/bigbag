package github.chains.breakingclassifier;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class ErrorReportAggregator {

    private ErrorReportAggregator() {
    }

    static BreakingReport aggregate(Path originalFailurePath, List<BreakingError> errors) {
        String failurePath = originalFailurePath != null ? originalFailurePath.toString() : "";

        if (errors == null || errors.isEmpty()) {
            return new BreakingReport(failurePath, FailureCategory.UNKNOWN, List.of());
        }

        FailureCategory reportCategory = errors.stream()
                .map(BreakingError::failureCategory)
                .filter(cat -> cat != FailureCategory.UNKNOWN)
                .findFirst()
                .orElse(FailureCategory.UNKNOWN);

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
}

