package com.example.core.bump.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Represents a unique version combination (groupId:artifactId:previousVersion -> newVersion)
 * with metadata about how many times it appears and in which projects.
 */
public record VersionCombination(
        @JsonProperty("dependencyGroupId") String dependencyGroupId,
        @JsonProperty("dependencyArtifactId") String dependencyArtifactId,
        @JsonProperty("previousVersion") String previousVersion,
        @JsonProperty("newVersion") String newVersion,
        @JsonProperty("count") int count,
        @JsonProperty("projects") List<String> projects,
        @JsonProperty("breakingCommits") List<String> breakingCommits,
        @JsonProperty("failureCategories") List<String> failureCategories,
        @JsonProperty("apiDiffLines") Integer apiDiffLines
) {
}

