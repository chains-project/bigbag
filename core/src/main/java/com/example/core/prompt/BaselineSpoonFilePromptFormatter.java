package com.example.core.prompt;

import com.example.core.model.BreakingUpdateRecord;
import com.example.core.model.ClassificationSummary;
import com.example.core.service.ChangeImpactReportService.FileImpact;

import java.util.Map;

/**
 * Formatter that implements the baseline Spoon prompt defined in
 * {@code prompts/baseline_spoon.txt}.
 *
 * This formatter generates Spoon transformation rules with minimal instructions,
 * focusing on executable Java code output. The prompt expects the LLM to output
 * ONLY executable Java code (a complete Spoon processor class) without explanations
 * or markdown formatting.
 */
public class BaselineSpoonFilePromptFormatter implements FilePromptFormatter {

    @Override
    public String id() {
        return "baseline_spoon";
    }

    @Override
    public String build(BreakingUpdateRecord record,
                        ClassificationSummary summary,
                        Map<String, String> globals,
                        FileImpact fileImpact) {
        String apiDiff = globals.getOrDefault("DEPENDENCY_CHANGE_DIFF", "");
        
        return """
            <task>
            Write Spoon-based Java source code transformation rules to fix this breakage.
            </task>
            
            <input>
            <dependency_change_diff>
            %s
           </dependency_change_diff>
            </input>
            
            <requirements>
            - Use ONLY the Spoon framework
            - Output ONLY executable Java code
            - NO explanations or markdown formatting
            - Include all necessary imports
            </requirements>
            
            <output_format>
            Single Java file with:
            1. Spoon imports
            2. Processor class extending AbstractProcessor
            3. process() method with transformation logic
            </output_format>
            """.formatted(apiDiff);
    }
}
