package github.chains.breakingclassifier;

import java.util.List;

public record ErrorDetail(int lineNumber,
                          Integer columnNumber,
                          String message,
                          List<String> details) {

    public ErrorDetail {
        details = details == null ? List.of() : List.copyOf(details);
    }
}

