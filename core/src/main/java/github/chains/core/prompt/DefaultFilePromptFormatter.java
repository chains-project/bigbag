package github.chains.core.prompt;

import github.chains.core.model.BreakingUpdateRecord;
import github.chains.core.model.ClassificationSummary;
import github.chains.core.service.ChangeImpactReportService.FileImpact;

import java.util.Map;

/**
 * Default, compact prompt style that encodes the essential context:
 * project, file path, errors, and only the API changes that relate to
 * the failing constructs in this file.
 */
public class DefaultFilePromptFormatter implements FilePromptFormatter {

    @Override
    public String id() {
        return "default";
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
            
            RELATED API CHANGES FOR THIS FILE
            %s
            
            """.formatted(related);
        
        return """
            You are assisting with fixing breaking dependency updates in a Java project.
            
            PROJECT
              Name: %s
              Breaking commit: %s
              Dataset category: %s
              Inferred category: %s
            
            FILE WITH ERRORS
              Path: %s
              Error count: %s
            
            ERRORS IN THIS FILE
            %s%sAUXILIARY FILES (optional, for more context):
              breaking-classifier-report.json: %s
              change-impact.json: %s
              breaking-changes.json: %s
            
            Your task: propose code changes to fix the compilation/test errors in this file, taking into account the dependency breaking changes described above.
            """.formatted(project, breakingCommit, datasetCategory, inferredCategory, 
                         filePath, fileErrorCount, fileErrors, relatedSection,
                         classifierReport, changeImpactReport, breakingChangesReport);
    }
}


