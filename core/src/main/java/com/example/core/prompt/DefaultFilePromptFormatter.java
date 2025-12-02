package com.example.core.prompt;

import com.example.core.model.BreakingUpdateRecord;
import com.example.core.model.ClassificationSummary;
import com.example.core.service.ChangeImpactReportService.FileImpact;

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
        StringBuilder sb = new StringBuilder();

        sb.append("You are assisting with fixing breaking dependency updates in a Java project.")
                .append(System.lineSeparator())
                .append(System.lineSeparator());

        sb.append("PROJECT").append(System.lineSeparator());
        sb.append("  Name: ").append(globals.getOrDefault("PROJECT", "unknown")).append(System.lineSeparator());
        sb.append("  Breaking commit: ").append(globals.getOrDefault("BREAKING_COMMIT", "unknown")).append(System.lineSeparator());
        sb.append("  Dataset category: ").append(globals.getOrDefault("DATASET_CATEGORY", "unknown")).append(System.lineSeparator());
        sb.append("  Inferred category: ").append(globals.getOrDefault("INFERRED_CATEGORY", "unknown")).append(System.lineSeparator());
        sb.append(System.lineSeparator());

        sb.append("FILE WITH ERRORS").append(System.lineSeparator());
        sb.append("  Path: ").append(globals.getOrDefault("FILE_PATH", "")).append(System.lineSeparator());
        sb.append("  Error count: ").append(globals.getOrDefault("FILE_ERROR_COUNT", "0")).append(System.lineSeparator());
        sb.append(System.lineSeparator());

        sb.append("ERRORS IN THIS FILE").append(System.lineSeparator());
        sb.append(globals.getOrDefault("FILE_ERRORS", "")).append(System.lineSeparator());
        sb.append(System.lineSeparator());

        String related = globals.getOrDefault("FILE_RELATED_CHANGES", "");
        if (!related.isBlank()) {
            sb.append("RELATED API CHANGES FOR THIS FILE").append(System.lineSeparator());
            sb.append(related).append(System.lineSeparator());
            sb.append(System.lineSeparator());
        }

        sb.append("AUXILIARY FILES (optional, for more context):").append(System.lineSeparator());
        sb.append("  breaking-classifier-report.json: ")
                .append(globals.getOrDefault("BREAKING_CLASSIFIER_REPORT", ""))
                .append(System.lineSeparator());
        sb.append("  change-impact.json: ")
                .append(globals.getOrDefault("CHANGE_IMPACT_REPORT", ""))
                .append(System.lineSeparator());
        sb.append("  breaking-changes.json: ")
                .append(globals.getOrDefault("BREAKING_CHANGES_REPORT", ""))
                .append(System.lineSeparator());

        sb.append(System.lineSeparator());
        sb.append("Your task: propose code changes to fix the compilation/test errors in this file, ");
        sb.append("taking into account the dependency breaking changes described above.");

        return sb.toString();
    }
}


