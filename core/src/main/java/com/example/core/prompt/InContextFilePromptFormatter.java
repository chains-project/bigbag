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
        String nl = System.lineSeparator();
        StringBuilder sb = new StringBuilder();

        sb.append("=== TASK ===").append(nl);
        sb.append("You are an expert Java developer helping to repair a client project ")
                .append("after a breaking dependency update.").append(nl).append(nl);

        sb.append("=== PROJECT CONTEXT ===").append(nl);
        sb.append("Project: ").append(globals.getOrDefault("PROJECT", "unknown")).append(nl);
        sb.append("Breaking commit: ").append(globals.getOrDefault("BREAKING_COMMIT", "unknown")).append(nl);
        sb.append("Dataset category: ").append(globals.getOrDefault("DATASET_CATEGORY", "unknown")).append(nl);
        sb.append("Inferred category: ").append(globals.getOrDefault("INFERRED_CATEGORY", "unknown")).append(nl);
        sb.append(nl);

        sb.append("=== FILE UNDER ANALYSIS ===").append(nl);
        sb.append("File path: ").append(globals.getOrDefault("FILE_PATH", "")).append(nl);
        sb.append("Error count: ").append(globals.getOrDefault("FILE_ERROR_COUNT", "0")).append(nl);
        sb.append(nl);

        sb.append("Reported errors for this file:").append(nl);
        sb.append(globals.getOrDefault("FILE_ERRORS", "")).append(nl).append(nl);

        String related = globals.getOrDefault("FILE_RELATED_CHANGES", "");
        if (!related.isBlank()) {
            sb.append("=== RELATED API CHANGES FOR THESE ERRORS ===").append(nl);
            sb.append(related).append(nl).append(nl);
        }

        sb.append("=== AVAILABLE ARTIFACTS (ON DISK) ===").append(nl);
        sb.append("- breaking-classifier-report.json: ")
                .append(globals.getOrDefault("BREAKING_CLASSIFIER_REPORT", ""))
                .append(nl);
        sb.append("- change-impact.json: ")
                .append(globals.getOrDefault("CHANGE_IMPACT_REPORT", ""))
                .append(nl);
        sb.append("- breaking-changes.json: ")
                .append(globals.getOrDefault("BREAKING_CHANGES_REPORT", ""))
                .append(nl);
        sb.append(nl);

        sb.append("=== INSTRUCTIONS ===").append(nl);
        sb.append("1. Infer how the dependency's breaking changes affect this file.").append(nl);
        sb.append("2. Suggest concrete Java edits that fix the errors while respecting the new API.").append(nl);
        sb.append("3. Keep changes minimal but correct; do not modify unrelated code.").append(nl);

        return sb.toString();
    }
}


