package com.example.spoonanalyzer.model;

import java.nio.file.Path;
import java.util.Objects;

public final class DependencyInfo {

    private final DependencyOrigin origin;
    private final String groupId;
    private final String artifactId;
    private final String version;
    private final Path sourcePath;

    private DependencyInfo(Builder builder) {
        this.origin = builder.origin;
        this.groupId = builder.groupId;
        this.artifactId = builder.artifactId;
        this.version = builder.version;
        this.sourcePath = builder.sourcePath;
    }

    public static Builder builder(DependencyOrigin origin) {
        return new Builder(origin);
    }

    public DependencyOrigin getOrigin() {
        return origin;
    }

    public String getGroupId() {
        return groupId;
    }

    public String getArtifactId() {
        return artifactId;
    }

    public String getVersion() {
        return version;
    }

    public Path getSourcePath() {
        return sourcePath;
    }

    public String shortCoordinates() {
        if (groupId == null || artifactId == null) {
            return origin.name();
        }
        if (version == null) {
            return groupId + ":" + artifactId;
        }
        return groupId + ":" + artifactId + ":" + version;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("origin=").append(origin.name());
        if (groupId != null && artifactId != null) {
            builder.append(", coordinates=").append(shortCoordinates());
        }
        if (sourcePath != null) {
            builder.append(", sourcePath=").append(sourcePath);
        }
        return builder.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof DependencyInfo)) {
            return false;
        }
        DependencyInfo that = (DependencyInfo) o;
        return origin == that.origin
                && Objects.equals(groupId, that.groupId)
                && Objects.equals(artifactId, that.artifactId)
                && Objects.equals(version, that.version)
                && Objects.equals(sourcePath, that.sourcePath);
    }

    @Override
    public int hashCode() {
        return Objects.hash(origin, groupId, artifactId, version, sourcePath);
    }

    public static final class Builder {
        private final DependencyOrigin origin;
        private String groupId;
        private String artifactId;
        private String version;
        private Path sourcePath;

        private Builder(DependencyOrigin origin) {
            this.origin = Objects.requireNonNull(origin, "origin");
        }

        public Builder groupId(String groupId) {
            this.groupId = groupId;
            return this;
        }

        public Builder artifactId(String artifactId) {
            this.artifactId = artifactId;
            return this;
        }

        public Builder version(String version) {
            this.version = version;
            return this;
        }

        public Builder sourcePath(Path sourcePath) {
            this.sourcePath = sourcePath;
            return this;
        }

        public DependencyInfo build() {
            return new DependencyInfo(this);
        }
    }
}

