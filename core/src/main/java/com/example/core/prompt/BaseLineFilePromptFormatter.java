package com.example.core.prompt;

import com.example.core.model.BreakingUpdateRecord;
import com.example.core.model.ClassificationSummary;
import com.example.core.service.ChangeImpactReportService.FileImpact;

import java.util.Map;

/**
 * Formatter that implements the baseline prompt defined in
 * {@code prompts/baseLine.txt}.
 *
 * This is a simple, direct prompt that generates Spoon transformation rules
 * without in-context examples, focusing on clear instructions.
 */
public class BaseLineFilePromptFormatter implements FilePromptFormatter {

    @Override
    public String id() {
        return "baseline";
    }

    @Override
    public String build(BreakingUpdateRecord record,
                        ClassificationSummary summary,
                        Map<String, String> globals,
                        FileImpact fileImpact) {
        String apiDiff = globals.getOrDefault("DEPENDENCY_CHANGE_DIFF", "");
        
        return """
            You are an expert Java developer and a specialist in the Spoon code transformation library. Your task is to generate **Spoon transformation rules** to automatically migrate client code after a dependency upgrade.
            
            You will be provided a **diff of the dependency changes**:
            
            <dependency_change_diff>
            %s
            </dependency_change_diff>
            
            **Important:**
            
            * Only generate **Spoon transformation rules**.
            * Do **not** include explanations, analysis, or instructions.
            * The output must be **ready to copy and execute**.
            
            ### **Instructions to Follow**
            
            Analyze the dependency change diff and generate **only Spoon transformation rules** for each change identified. For each transformation:
            
            * Generate **ready-to-execute Java Spoon code blocks**
            * Apply **contextual inference** for parameters and method replacements when needed
            * Update imports and fully-qualified class names when classes are moved or renamed
            * Handle return type changes by adapting consuming code appropriately
            * Use appropriate Spoon API methods for creating, modifying, and replacing code elements
            
            **Output Requirements:**
            * **Do not include explanations, commentary, or step-by-step reasoning**
            * **Do not include change descriptions or analysis**
            * Output must be **clean Java Spoon code blocks only**, one per transformation
            * Each code block should be properly formatted and ready to copy and execute
            
            Generate the Spoon transformation rules now:
            """.formatted(apiDiff);
    }
}

