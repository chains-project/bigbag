package chains.changeimpact.model;

import github.chains.spoonanalyzer.model.DependencyInfo;
import github.chains.spoonanalyzer.model.DependencyOrigin;

import java.nio.file.Path;

/**
 * Simplified view of dependency coordinates for serialization.
 */
public record DependencySummary(String origin,
                                String groupId,
                                String artifactId,
                                String version,
                                String coordinates,
                                String sourcePath) {

    public static DependencySummary from(DependencyInfo info) {
        if (info == null) {
            return new DependencySummary(DependencyOrigin.UNKNOWN.name(), null, null, null, DependencyOrigin.UNKNOWN.name(), null);
        }
        String coordinates = info.shortCoordinates();
        Path source = info.getSourcePath();
        return new DependencySummary(
                info.getOrigin().name(),
                info.getGroupId(),
                info.getArtifactId(),
                info.getVersion(),
                coordinates,
                source != null ? source.toString() : null
        );
    }
}

