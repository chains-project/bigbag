package chains.changeimpact.model;

import java.util.List;
import java.util.Objects;

/**
 * Top-level DTO for the generated report.
 */
public record ChangeImpactReport(String projectPath,
                                 String sourceFile,
                                 int lineNumber,
                                 String oldJar,
                                 String newJar,
                                 String generatedAt,
                                 List<ConstructImpact> constructs) {

    public ChangeImpactReport {
        Objects.requireNonNull(projectPath, "projectPath");
        Objects.requireNonNull(sourceFile, "sourceFile");
        Objects.requireNonNull(oldJar, "oldJar");
        Objects.requireNonNull(newJar, "newJar");
        Objects.requireNonNull(generatedAt, "generatedAt");
        constructs = constructs == null ? List.of() : List.copyOf(constructs);
    }
}

