package chains.changeimpact.model;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Immutable container describing the inputs required to compute the change-impact report.
 */
public record ChangeImpactRequest(Path projectPath,
                                  Path sourceFile,
                                  int lineNumber,
                                  Path oldJar,
                                  Path newJar) {

    public ChangeImpactRequest {
        Objects.requireNonNull(projectPath, "projectPath");
        Objects.requireNonNull(sourceFile, "sourceFile");
        if (lineNumber < 1) {
            throw new IllegalArgumentException("lineNumber must be >= 1");
        }
        Objects.requireNonNull(oldJar, "oldJar");
        Objects.requireNonNull(newJar, "newJar");
    }
}

