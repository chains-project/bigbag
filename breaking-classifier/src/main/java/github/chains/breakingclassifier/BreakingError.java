package github.chains.breakingclassifier;

import java.util.List;

/**
 * Represents a compiler error extracted from a Maven build log.
 *
 * @param filePath     path to the Java source file that triggered the error
 * @param lineNumber   line number reported by the compiler
 * @param columnNumber optional column number reported by the compiler
 * @param message      main compiler message (e.g., "cannot find symbol")
 * @param details      additional lines that describe the error (e.g., symbol, location)
 */
public record BreakingError(String filePath,
                            int lineNumber,
                            Integer columnNumber,
                            String message,
                            List<String> details,
                            FailureCategory failureCategory) {

    public BreakingError {
        details = details == null ? List.of() : List.copyOf(details);
        failureCategory = failureCategory == null ? FailureCategory.UNKNOWN : failureCategory;
    }
}

