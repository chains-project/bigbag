package com.example.core.prompt;

import com.example.core.model.BreakingUpdateRecord;
import com.example.core.model.ClassificationSummary;
import com.example.core.service.ChangeImpactReportService.FileImpact;

import java.util.Map;

/**
 * Strategy interface for building a prompt string for a single file with errors.
 * <p>
 * Inspired by Bacardi's prompt builders ({@code se.kth.prompt.*}) where each
 * implementation encodes a specific prompt style.
 */
public interface FilePromptFormatter {

    /**
     * Logical identifier for this formatter (e.g., "final", "in_context", "rq3").
     * Used to map environment configuration to implementations.
     */
    String id();

    /**
     * Build a complete prompt string for a given file, using global context and
     * per-file error information.
     *
     * @param record     dataset record
     * @param summary    classification summary
     * @param globals    global placeholder values (project, dependency, diff, paths, etc.)
     * @param fileImpact file-level impact (path + errors)
     * @return fully rendered prompt text
     */
    String build(BreakingUpdateRecord record,
                 ClassificationSummary summary,
                 Map<String, String> globals,
                 FileImpact fileImpact);
}


