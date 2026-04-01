package github.chains.core.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record BreakingUpdateRecord(
        @JsonProperty("url") String url,
        @JsonProperty("project") String project,
        @JsonProperty("projectOrganisation") String projectOrganisation,
        @JsonProperty("breakingCommit") String breakingCommit,
        @JsonProperty("prAuthor") String pullRequestAuthor,
        @JsonProperty("preCommitAuthor") String preCommitAuthor,
        @JsonProperty("breakingCommitAuthor") String breakingCommitAuthor,
        @JsonProperty("updatedDependency") UpdatedDependency updatedDependency,
        @JsonProperty("preCommitReproductionCommand") String preCommitReproductionCommand,
        @JsonProperty("breakingUpdateReproductionCommand") String breakingUpdateReproductionCommand,
        @JsonProperty("javaVersionUsedForReproduction") String javaVersionUsedForReproduction,
        @JsonProperty("failureCategory") String failureCategory,
        @JsonProperty("licenseInfo") String licenseInfo
) {
    public String descriptor() {
        return project + "-" + breakingCommit;
    }
}

