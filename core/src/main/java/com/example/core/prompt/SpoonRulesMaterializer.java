package com.example.core.prompt;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Utility to post-process the output of LLM prompts that contain Spoon rules.
 * <p>
 * Given:
 * <ul>
 *   <li>the LLM answer file (containing a &lt;spoon_rules&gt; section), and</li>
 *   <li>the original Java source file to transform,</li>
 * </ul>
 * this class extracts the rules and generates two artefacts inside a
 * per-commit subdirectory under the reports folder:
 * <ul>
 *   <li>{@code {baseName}_spoon_rules_raw.txt} – raw rules as produced by the LLM</li>
 *   <li>{@code {baseName}_spoon_apply.java} – a Spoon driver skeleton that embeds
 *       the rules and is ready to be compiled/extended to apply them to the
 *       original source file</li>
 * </ul>
 */
public final class SpoonRulesMaterializer {

    private SpoonRulesMaterializer() {
        // utility
    }

    /**
     * Extract Spoon rules from an LLM answer file and generate:
     * <ol>
     *   <li>a raw rules file, and</li>
     *   <li>a Java driver skeleton to apply those rules with Spoon</li>
     * </ol>
     * Both files are written under {@code commitReportDir/spoon-rules/}.
     *
     * @param promptOutputFile  file containing the LLM answer (with &lt;spoon_rules&gt; ... &lt;/spoon_rules&gt;)
     * @param originalSourceFile original Java source file to which the rules should be applied
     * @param commitReportDir   reports/{commit} directory for this analysis
     * @param baseName          logical base name (e.g., sanitized file path) used for output files
     */
    public static void materialize(Path promptOutputFile,
                                   Path originalSourceFile,
                                   Path commitReportDir,
                                   String baseName) throws IOException {
        if (promptOutputFile == null || originalSourceFile == null || commitReportDir == null || baseName == null) {
            throw new IllegalArgumentException("promptOutputFile, originalSourceFile, commitReportDir and baseName must be non-null");
        }

        String content = Files.readString(promptOutputFile, StandardCharsets.UTF_8);
        String rules = extractSpoonRulesSection(content);

        Path targetDir = commitReportDir.resolve("spoon-rules");
        Files.createDirectories(targetDir);

        // 1) Raw rules as produced by the LLM
        Path rawRules = targetDir.resolve(baseName + "_spoon_rules_raw.txt");
        Files.writeString(rawRules, rules, StandardCharsets.UTF_8);

        // 2) Java driver skeleton that embeds the rules and sets up Spoon
        Path driver = targetDir.resolve(baseName + "_spoon_apply.java");
        String driverSource = buildDriverSource(originalSourceFile, rawRules, rules);
        Files.writeString(driver, driverSource, StandardCharsets.UTF_8);
    }

    /**
     * Extract the content of the &lt;spoon_rules&gt; section if present; otherwise
     * return the full content.
     */
    private static String extractSpoonRulesSection(String content) {
        if (content == null || content.isBlank()) {
            return "";
        }
        String lower = content.toLowerCase();
        int start = lower.indexOf("<spoon_rules>");
        int end = lower.indexOf("</spoon_rules>");
        if (start >= 0 && end > start) {
            int from = start + "<spoon_rules>".length();
            return content.substring(from, end).trim();
        }
        // Fallback: return the whole file
        return content.trim();
    }

    /**
     * Build a minimal Spoon driver that:
     * <ul>
     *   <li>configures a Launcher for the original source file</li>
     *   <li>contains an explicit section where the extracted rules are embedded</li>
     * </ul>
     */
    private static String buildDriverSource(Path originalSourceFile,
                                            Path rawRulesFile,
                                            String rules) {
        String nl = System.lineSeparator();
        StringBuilder sb = new StringBuilder();

        sb.append("import spoon.Launcher;").append(nl);
        sb.append("import spoon.reflect.factory.Factory;").append(nl);
        sb.append("import spoon.processing.Processor;").append(nl);
        sb.append(nl);
        sb.append("/**").append(nl);
        sb.append(" * Auto-generated Spoon driver to apply LLM-generated rules to:").append(nl);
        sb.append(" *   ").append(originalSourceFile.toString()).append(nl);
        sb.append(" * ").append(nl);
        sb.append(" * Raw rules file: ").append(rawRulesFile.toString()).append(nl);
        sb.append(" * ").append(nl);
        sb.append(" * The section marked BEGIN_SPOON_RULES / END_SPOON_RULES contains").append(nl);
        sb.append(" * the rules as returned by the LLM. You may need to wrap them into").append(nl);
        sb.append(" * proper Spoon Processors or integrate them into the main method").append(nl);
        sb.append(" * depending on their structure.").append(nl);
        sb.append(" */").append(nl);
        sb.append("public class SpoonApplyRules {").append(nl).append(nl);

        sb.append("    public static void main(String[] args) {").append(nl);
        sb.append("        Launcher launcher = new Launcher();").append(nl);
        sb.append("        launcher.getEnvironment().setNoClasspath(true);").append(nl);
        sb.append("        launcher.addInputResource(\"").append(originalSourceFile.toString().replace("\\", "/")).append("\");").append(nl);
        sb.append("        Factory factory = launcher.getFactory();").append(nl).append(nl);
        sb.append("        // TODO: register your Spoon processors or transformations here").append(nl);
        sb.append("        // Example: launcher.addProcessor(new MyProcessor());").append(nl).append(nl);
        sb.append("        launcher.buildModel();").append(nl);
        sb.append("        launcher.process();").append(nl);
        sb.append("    }").append(nl).append(nl);

        sb.append("    // === BEGIN_SPOON_RULES (raw content from LLM) ===").append(nl);
        sb.append("    /*").append(nl);
        if (rules != null && !rules.isBlank()) {
            for (String line : rules.split("\\r?\\n")) {
                sb.append("     * ").append(line).append(nl);
            }
        }
        sb.append("     */").append(nl);
        sb.append("    // === END_SPOON_RULES ===").append(nl);

        sb.append("}").append(nl);

        return sb.toString();
    }
}


