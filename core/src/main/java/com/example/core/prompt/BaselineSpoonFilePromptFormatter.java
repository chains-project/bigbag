package com.example.core.prompt;

import com.example.core.model.BreakingUpdateRecord;
import com.example.core.model.ClassificationSummary;
import com.example.core.service.ChangeImpactReportService.FileImpact;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

/**
 * Formatter that implements the baseline Spoon prompt defined in
 * {@code prompts/baseline_spoon.txt}.
 *
 * This formatter reads the template file and replaces placeholders with actual values.
 * It generates Spoon transformation rules with minimal instructions, focusing on
 * executable Java code output.
 */
public class BaselineSpoonFilePromptFormatter implements FilePromptFormatter {

    private static final Logger log = LoggerFactory.getLogger(BaselineSpoonFilePromptFormatter.class);
    private static final String TEMPLATE_PATH = "prompts/baseline_spoon.txt";

    @Override
    public String id() {
        return "baseline_spoon";
    }

    @Override
    public String build(BreakingUpdateRecord record,
                        ClassificationSummary summary,
                        Map<String, String> globals,
                        FileImpact fileImpact) {
        String template = loadTemplate();
        String apiDiff = globals.getOrDefault("DEPENDENCY_CHANGE_DIFF", "");
        
        // Replace placeholders in the template
        return template.replace("{{DEPENDENCY_CHANGE_DIFF}}", apiDiff);
    }

    /**
     * Load the template file from the prompts directory.
     * The template is expected to be in the project root under prompts/baseline_spoon.txt.
     * Tries multiple strategies to locate the file:
     * 1. Relative to current working directory
     * 2. Relative to core/ subdirectory
     * 3. By finding project root (looking for pom.xml)
     */
    private String loadTemplate() {
        // Strategy 1: Try relative to current working directory
        Path templatePath = Paths.get(TEMPLATE_PATH);
        if (Files.exists(templatePath)) {
            return readTemplateFile(templatePath);
        }

        // Strategy 2: Try relative to core/ subdirectory
        Path altPath = Paths.get("core", TEMPLATE_PATH);
        if (Files.exists(altPath)) {
            return readTemplateFile(altPath);
        }

        // Strategy 3: Try to find project root by looking for pom.xml
        Path projectRoot = findProjectRoot();
        if (projectRoot != null) {
            Path rootTemplatePath = projectRoot.resolve(TEMPLATE_PATH);
            if (Files.exists(rootTemplatePath)) {
                return readTemplateFile(rootTemplatePath);
            }
        }

        log.warn("Template file not found, using fallback template");
        return getFallbackTemplate();
    }

    /**
     * Attempts to find the project root by looking for pom.xml in parent directories.
     */
    private Path findProjectRoot() {
        Path current = Paths.get(".").toAbsolutePath().normalize();
        Path check = current;
        int maxDepth = 10; // Limit search depth
        int depth = 0;
        
        while (check != null && depth < maxDepth) {
            if (Files.exists(check.resolve("pom.xml"))) {
                return check;
            }
            Path parent = check.getParent();
            if (parent == null || parent.equals(check)) {
                break;
            }
            check = parent;
            depth++;
        }
        return null;
    }

    /**
     * Reads the template file from the given path.
     */
    private String readTemplateFile(Path templatePath) {
        try {
            return Files.readString(templatePath, StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.warn("Failed to read template from {}: {}, using fallback template", 
                    templatePath, e.getMessage());
            return getFallbackTemplate();
        }
    }

    /**
     * Fallback template in case the file cannot be loaded.
     * This matches the content of prompts/baseline_spoon.txt.
     */
    private String getFallbackTemplate() {
        return """
            <task>
            Write Spoon-based Java source code transformation rules to fix this breakage.
            </task>
            
            <input>
            <dependency_change_diff>
            {{DEPENDENCY_CHANGE_DIFF}}
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
            1. Package declaration
            2. Spoon imports
            3. Processor class extending AbstractProcessor
            4. process() method with transformation logic
            </output_format>
            """;
    }
}

