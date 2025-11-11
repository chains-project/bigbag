package chains.changeimpact.model;

import java.util.List;

/**
 * Describes the Spoon construct and its associated API changes.
 */
public record ConstructImpact(String constructType,
                              String signature,
                              DependencySummary dependency,
                              Integer lineNumber,
                              String codeLine,
                              List<ApiChangeMatch> apiChanges) {

    public ConstructImpact {
        apiChanges = apiChanges == null ? List.of() : List.copyOf(apiChanges);
    }
}

