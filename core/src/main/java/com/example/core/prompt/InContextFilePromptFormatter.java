package com.example.core.prompt;

import com.example.core.model.BreakingUpdateRecord;
import com.example.core.model.ClassificationSummary;
import com.example.core.service.ChangeImpactReportService.FileImpact;

import java.util.Map;

/**
 * More verbose, "in-context" style prompt similar in spirit to Bacardi's
 * in-context templates: clearly separates sections and emphasises the
 * specific API changes that relate to the failing constructs/lines.
 */
public class InContextFilePromptFormatter implements FilePromptFormatter {

    @Override
    public String id() {
        return "in_context";
    }

    @Override
    public String build(BreakingUpdateRecord record,
                        ClassificationSummary summary,
                        Map<String, String> globals,
                        FileImpact fileImpact) {
        String project = globals.getOrDefault("PROJECT", "unknown");
        String breakingCommit = globals.getOrDefault("BREAKING_COMMIT", "unknown");
        String datasetCategory = globals.getOrDefault("DATASET_CATEGORY", "unknown");
        String inferredCategory = globals.getOrDefault("INFERRED_CATEGORY", "unknown");
        String filePath = globals.getOrDefault("FILE_PATH", "");
        String fileErrorCount = globals.getOrDefault("FILE_ERROR_COUNT", "0");
        String fileErrors = globals.getOrDefault("FILE_ERRORS", "");
        String related = globals.getOrDefault("FILE_RELATED_CHANGES", "");
        String classifierReport = globals.getOrDefault("BREAKING_CLASSIFIER_REPORT", "");
        String changeImpactReport = globals.getOrDefault("CHANGE_IMPACT_REPORT", "");
        String breakingChangesReport = globals.getOrDefault("BREAKING_CHANGES_REPORT", "");
        
        String relatedSection = related.isBlank() ? "" : """
            
            === RELATED API CHANGES FOR THESE ERRORS ===
            %s
            
            """.formatted(related);
        
        return """
            === TASK ===
            You are an expert Java developer helping to repair a client project after a breaking dependency update.
            
            === PROJECT CONTEXT ===
            Project: %s
            Breaking commit: %s
            Dataset category: %s
            Inferred category: %s
            
            === FILE UNDER ANALYSIS ===
            File path: %s
            Error count: %s
            
            Reported errors for this file:
            %s%s=== AVAILABLE ARTIFACTS (ON DISK) ===
            - breaking-classifier-report.json: %s
            - change-impact.json: %s
            - breaking-changes.json: %s
            
            === INSTRUCTIONS ===
            1. Infer how the dependency's breaking changes affect this file.
            2. Suggest concrete Java edits that fix the errors while respecting the new API.
            3. Keep changes minimal but correct; do not modify unrelated code.
            """.formatted(project, breakingCommit, datasetCategory, inferredCategory,
                         filePath, fileErrorCount, fileErrors, relatedSection,
                         classifierReport, changeImpactReport, breakingChangesReport);
    }
}


