package github.chains.core.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record UpdatedDependency(
        @JsonProperty("dependencyGroupID") String dependencyGroupId,
        @JsonProperty("dependencyArtifactID") String dependencyArtifactId,
        @JsonProperty("previousVersion") String previousVersion,
        @JsonProperty("newVersion") String newVersion,
        @JsonProperty("dependencyScope") String dependencyScope,
        @JsonProperty("versionUpdateType") String versionUpdateType,
        @JsonProperty("githubCompareLink") String githubCompareLink,
        @JsonProperty("mavenSourceLinkPre") String mavenSourceLinkPre,
        @JsonProperty("mavenSourceLinkBreaking") String mavenSourceLinkBreaking,
        @JsonProperty("updatedFileType") String updatedFileType,
        @JsonProperty("dependencySection") String dependencySection,
        @JsonProperty("licenseInfo") String licenseInfo,
        @JsonProperty("githubRepoSlug") String githubRepoSlug
) {
    public String classifier() {
        return dependencyGroupId + ":" + dependencyArtifactId;
    }
}

