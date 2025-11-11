package github.chains.breakingclassifier;

import java.util.List;
import java.util.Objects;

public record FileErrorGroup(String filePath,
                             List<ErrorDetail> errors) {

    public FileErrorGroup {
        Objects.requireNonNull(filePath, "filePath");
        errors = errors == null ? List.of() : List.copyOf(errors);
    }
}

