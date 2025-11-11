package github.chains.breakingclassifier;

import java.util.List;
import java.util.Objects;

public record BreakingReport(String originalFailurePath,
                             FailureCategory failureCategory,
                             List<FileErrorGroup> errorsByFile) {

    public BreakingReport {
        Objects.requireNonNull(originalFailurePath, "originalFailurePath");
        failureCategory = failureCategory == null ? FailureCategory.UNKNOWN : failureCategory;
        errorsByFile = errorsByFile == null ? List.of() : List.copyOf(errorsByFile);
    }
}

