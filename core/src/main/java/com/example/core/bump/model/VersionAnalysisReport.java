package com.example.core.bump.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Report containing analysis of unique version combinations.
 */
public record VersionAnalysisReport(
        @JsonProperty("totalRecords") int totalRecords,
        @JsonProperty("filteredRecords") int filteredRecords,
        @JsonProperty("failureCategory") String failureCategory,
        @JsonProperty("uniqueCombinations") int uniqueCombinations,
        @JsonProperty("combinations") List<VersionCombination> combinations
) {
}

