package com.example.core.bump;

import com.example.core.model.BreakingUpdateRecord;
import com.example.core.model.UpdatedDependency;
import com.example.core.pipeline.FailureCategoryUtils;
import com.example.core.bump.model.VersionCombination;
import com.example.core.bump.model.VersionAnalysisReport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.kth.models.FailureCategory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Analyzes breaking update records to find unique version combinations.
 */
public class VersionCombinationAnalyzer {
    
    private static final Logger log = LoggerFactory.getLogger(VersionCombinationAnalyzer.class);
    
    /**
     * Analyzes breaking update records and finds unique version combinations.
     *
     * @param records the list of breaking update records
     * @param filterCategory optional failure category filter (null = no filter)
     * @return analysis report with unique combinations and records map
     */
    public AnalysisResult analyze(List<BreakingUpdateRecord> records, FailureCategory filterCategory) {
        log.info("Analyzing {} records", records.size());
        
        // Filter by category if specified
        List<BreakingUpdateRecord> filteredRecords = records;
        if (filterCategory != null) {
            final FailureCategory finalFilterCategory = filterCategory;
            filteredRecords = records.stream()
                    .filter(record -> FailureCategoryUtils.matchesFailureCategory(record, finalFilterCategory))
                    .collect(Collectors.toList());
            log.info("Filtered to {} records matching category: {}", filteredRecords.size(), filterCategory);
        }
        
        // Map to store version combinations: (groupId:artifactId:previousVersion -> newVersion) -> combination data
        Map<String, VersionCombinationData> combinationMap = new HashMap<>();
        // Map to store records for each combination
        Map<String, List<BreakingUpdateRecord>> recordsByCombination = new HashMap<>();
        
        // Process each record
        for (BreakingUpdateRecord record : filteredRecords) {
            UpdatedDependency dependency = record.updatedDependency();
            if (dependency == null) {
                log.debug("Skipping record {}: no updated dependency", record.descriptor());
                continue;
            }
            
            String dependencyGroupId = dependency.dependencyGroupId();
            String dependencyArtifactId = dependency.dependencyArtifactId();
            String previousVersion = dependency.previousVersion();
            String newVersion = dependency.newVersion();
            
            if (dependencyGroupId == null || dependencyArtifactId == null ||
                dependencyGroupId.trim().isEmpty() || dependencyArtifactId.trim().isEmpty()) {
                log.debug("Skipping record {}: missing dependency group or artifact ID", record.descriptor());
                continue;
            }
            
            if (previousVersion == null || newVersion == null || 
                previousVersion.trim().isEmpty() || newVersion.trim().isEmpty()) {
                log.debug("Skipping record {}: missing version information", record.descriptor());
                continue;
            }
            
            // Create key for this combination: groupId:artifactId:previousVersion -> newVersion
            String key = dependencyGroupId + ":" + dependencyArtifactId + ":" + 
                        previousVersion + " -> " + newVersion;
            
            // Get or create combination data
            VersionCombinationData data = combinationMap.computeIfAbsent(
                    key, 
                    k -> new VersionCombinationData(
                            dependencyGroupId, 
                            dependencyArtifactId, 
                            previousVersion, 
                            newVersion
                    )
            );
            
            // Update combination data
            data.incrementCount();
            
            // Add record to combination map
            recordsByCombination.computeIfAbsent(key, k -> new ArrayList<>()).add(record);
            
            // Add project if not already present
            if (record.project() != null && !data.projects.contains(record.project())) {
                data.projects.add(record.project());
            }
            
            // Add breaking commit if not already present
            if (record.breakingCommit() != null && !data.breakingCommits.contains(record.breakingCommit())) {
                data.breakingCommits.add(record.breakingCommit());
            }
            
            // Add failure category if not already present
            if (record.failureCategory() != null && !data.failureCategories.contains(record.failureCategory())) {
                data.failureCategories.add(record.failureCategory());
            }
        }
        
        // Convert to VersionCombination list and sort by count (descending)
        List<VersionCombination> combinations = combinationMap.values().stream()
                .map(data -> new VersionCombination(
                        data.dependencyGroupId,
                        data.dependencyArtifactId,
                        data.previousVersion,
                        data.newVersion,
                        data.count,
                        new ArrayList<>(data.projects),
                        new ArrayList<>(data.breakingCommits),
                        new ArrayList<>(data.failureCategories),
                        null // apiDiffLines will be set later by DependencyExtractor
                ))
                .sorted(Comparator.comparing(VersionCombination::count).reversed())
                .collect(Collectors.toList());
        
        log.info("Found {} unique version combinations", combinations.size());
        
        // Create report
        VersionAnalysisReport report = new VersionAnalysisReport(
                records.size(),
                filteredRecords.size(),
                filterCategory != null ? filterCategory.name() : null,
                combinations.size(),
                combinations
        );
        
        return new AnalysisResult(report, recordsByCombination);
    }
    
    /**
     * Result of analysis containing report and records map.
     */
    public static class AnalysisResult {
        private final VersionAnalysisReport report;
        private final Map<String, List<BreakingUpdateRecord>> recordsByCombination;
        
        public AnalysisResult(VersionAnalysisReport report, Map<String, List<BreakingUpdateRecord>> recordsByCombination) {
            this.report = report;
            this.recordsByCombination = recordsByCombination;
        }
        
        public VersionAnalysisReport report() {
            return report;
        }
        
        public Map<String, List<BreakingUpdateRecord>> recordsByCombination() {
            return recordsByCombination;
        }
    }
    
    /**
     * Internal data structure to accumulate combination information.
     */
    private static class VersionCombinationData {
        final String dependencyGroupId;
        final String dependencyArtifactId;
        final String previousVersion;
        final String newVersion;
        int count;
        final Set<String> projects = new HashSet<>();
        final Set<String> breakingCommits = new HashSet<>();
        final Set<String> failureCategories = new HashSet<>();
        
        VersionCombinationData(String dependencyGroupId, String dependencyArtifactId, 
                              String previousVersion, String newVersion) {
            this.dependencyGroupId = dependencyGroupId;
            this.dependencyArtifactId = dependencyArtifactId;
            this.previousVersion = previousVersion;
            this.newVersion = newVersion;
            this.count = 0;
        }
        
        void incrementCount() {
            this.count++;
        }
    }
}

