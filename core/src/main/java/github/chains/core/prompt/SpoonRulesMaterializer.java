package github.chains.core.prompt;

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
     *   <li>a Java driver skeleton to apply those rules with Spoon (or the complete class if LLM provided it)</li>
     * </ol>
     * Both files are written under {@code commitReportDir/spoon-rules/}.
     *
     * @param promptOutputFile  file containing the LLM answer (with &lt;spoon_rules&gt; ... &lt;/spoon_rules&gt;)
     * @param originalSourceFile original Java source file to which the rules should be applied
     * @param commitReportDir   reports/{commit} directory for this analysis
     * @param baseName          logical base name (e.g., sanitized file path) used for output files
     * @return the Path to the generated Java file (either SpoonApplyRules.java or the complete class from LLM)
     */
    public static Path materialize(Path promptOutputFile,
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

        // 2) Detect if the LLM output is a complete class or just rules
        String javaCode = extractJavaCode(rules);
        boolean isCompleteClass = isCompleteSpoonClass(javaCode);
        
        Path driver;
        if (isCompleteClass) {
            // LLM provided a complete class - adapt it and save it
            String adaptedClass = adaptCompleteClass(javaCode, originalSourceFile, commitReportDir);
            // Extract class name from the code or use a default
            String className = extractClassName(javaCode);
            driver = targetDir.resolve(className + ".java");
            Files.writeString(driver, adaptedClass, StandardCharsets.UTF_8);
        } else {
            // Only rules provided - generate the driver skeleton
            driver = targetDir.resolve("SpoonApplyRules.java");
            String driverSource = buildDriverSource(originalSourceFile, commitReportDir, rawRules, rules);
            Files.writeString(driver, driverSource, StandardCharsets.UTF_8);
        }
        
        return driver;
    }
    
    /**
     * Generates a diff file between the original and transformed Java files.
     * This should be called after the Spoon transformation has been executed.
     * The diff is saved in reports/{commit}/diff/ with the same name as the original file plus .diff extension.
     *
     * @param originalSourceFile the original source file path
     * @param commitReportDir the commit report directory (reports/{commit})
     * @param baseName the base name used for file identification (not used for diff filename)
     * @throws IOException if file operations fail
     * @throws InterruptedException if the diff process is interrupted
     */
    public static void generateDiffAfterTransformation(Path originalSourceFile,
                                                       Path commitReportDir,
                                                       String baseName) throws IOException, InterruptedException {
        // The transformed file should be in reports/{commit}/transformed/
        Path transformedDir = commitReportDir.resolve("transformed");
        String originalFileName = originalSourceFile.getFileName().toString();
        
        // Find the transformed file recursively (Spoon maintains package structure)
        Path transformedFile = findTransformedFile(transformedDir, originalFileName);
        
        if (transformedFile == null || !Files.exists(transformedFile)) {
            throw new IOException("Transformed file not found in: " + transformedDir);
        }
        
        // Save diff in reports/{commit}/diff/ directory
        // Each diff file corresponds to one original class and its transformed version
        Path diffDir = commitReportDir.resolve("diff");
        Path diffOutputFile = diffDir.resolve(originalFileName + ".diff");
        
        DiffGenerator.generateDiff(originalSourceFile, transformedFile, diffOutputFile);
    }
    
    /**
     * Helper method to find a transformed file recursively in a directory.
     */
    private static Path findTransformedFile(Path baseDir, String fileName) throws IOException {
        if (!Files.exists(baseDir)) {
            return null;
        }
        try (var paths = Files.walk(baseDir)) {
            return paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().equals(fileName))
                    .findFirst()
                    .orElse(null);
        }
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
     *   <li>saves transformed files to reports/{commit}/transformed/</li>
     * </ul>
     */
    private static String buildDriverSource(Path originalSourceFile,
                                            Path commitReportDir,
                                            Path rawRulesFile,
                                            String rules) {
        String nl = System.lineSeparator();
        StringBuilder sb = new StringBuilder();

        // Extract Java code from markdown code blocks if present
        String javaCode = extractJavaCode(rules);
        
        // Determine output directory: transformed files go in reports/{commit}/transformed/
        // This maintains the package structure while keeping transformed code separate from rules
        Path outputDir = commitReportDir.resolve("transformed");
        String outputDirPath = outputDir.toString().replace("\\", "/");
        
        // Add necessary imports
        sb.append("import spoon.Launcher;").append(nl);
        sb.append("import spoon.reflect.CtModel;").append(nl);
        sb.append("import spoon.reflect.factory.Factory;").append(nl);
        sb.append("import spoon.reflect.declaration.CtElement;").append(nl);
        sb.append("import spoon.reflect.declaration.CtMethod;").append(nl);
        sb.append("import spoon.reflect.declaration.CtType;").append(nl);
        sb.append("import spoon.reflect.code.CtInvocation;").append(nl);
        sb.append("import spoon.reflect.code.CtExpression;").append(nl);
        sb.append("import spoon.reflect.reference.CtExecutableReference;").append(nl);
        sb.append("import spoon.reflect.reference.CtTypeReference;").append(nl);
        sb.append("import spoon.reflect.visitor.filter.TypeFilter;").append(nl);
        sb.append("import java.util.Collections;").append(nl);
        sb.append("import java.util.List;").append(nl);
        sb.append("import java.io.File;").append(nl);
        sb.append(nl);
        
        sb.append("/**").append(nl);
        sb.append(" * Auto-generated Spoon driver to apply LLM-generated rules to:").append(nl);
        sb.append(" *   ").append(originalSourceFile.toString()).append(nl);
        sb.append(" * ").append(nl);
        sb.append(" * Raw rules file: ").append(rawRulesFile.toString()).append(nl);
        sb.append(" * ").append(nl);
        sb.append(" * The transformed file will be written to: ").append(outputDirPath).append(nl);
        sb.append(" * (maintaining the original package directory structure)").append(nl);
        sb.append(" */").append(nl);
        sb.append("public class SpoonApplyRules {").append(nl).append(nl);

        sb.append("    public static void main(String[] args) {").append(nl);
        sb.append("        Launcher launcher = new Launcher();").append(nl);
        sb.append("        launcher.getEnvironment().setNoClasspath(true);").append(nl);
        sb.append("        launcher.addInputResource(\"").append(originalSourceFile.toString().replace("\\", "/")).append("\");").append(nl);
        sb.append("        // Set output directory for transformed files").append(nl);
        sb.append("        launcher.setSourceOutputDirectory(new File(\"").append(outputDirPath).append("\"));").append(nl);
        sb.append(nl);
        sb.append("        launcher.buildModel();").append(nl);
        sb.append("        CtModel model = launcher.getModel();").append(nl);
        sb.append("        Factory factory = launcher.getFactory();").append(nl).append(nl);
        sb.append("        // Apply transformation rules").append(nl);
        sb.append("        applyTransformationRules(model, factory);").append(nl).append(nl);
        sb.append("        launcher.process();").append(nl);
        sb.append("        launcher.prettyprint();").append(nl);
        sb.append("        System.out.println(\"Transformed files written to: ").append(outputDirPath).append("\");").append(nl);
        sb.append("    }").append(nl).append(nl);

        sb.append("    private static void applyTransformationRules(CtModel model, Factory factory) {").append(nl);
        if (javaCode != null && !javaCode.isBlank()) {
            // Indent the extracted code and replace getFactory() with factory parameter
            String indentedCode = indentAndAdaptCode(javaCode, "        ");
            sb.append(indentedCode);
        } else {
            sb.append("        // No transformation rules provided").append(nl);
        }
        sb.append("    }").append(nl);
        sb.append("}").append(nl);

        return sb.toString();
    }
    
    /**
     * Extracts Java code from markdown code blocks (```java ... ```).
     * Extracts ALL code blocks and concatenates them.
     * If no code blocks are found, returns the original content.
     */
    private static String extractJavaCode(String content) {
        if (content == null || content.isBlank()) {
            return "";
        }
        
        StringBuilder allCode = new StringBuilder();
        String lower = content.toLowerCase();
        int searchStart = 0;
        
        // Extract all Java code blocks
        while (true) {
            int startIdx = lower.indexOf("```java", searchStart);
            if (startIdx < 0) {
                // Try generic code blocks
                startIdx = lower.indexOf("```", searchStart);
                if (startIdx < 0) {
                    break;
                }
            }
            
            int codeStart = startIdx + (lower.substring(startIdx).startsWith("```java") ? "```java".length() : 3);
            int endIdx = content.indexOf("```", codeStart);
            
            if (endIdx > codeStart) {
                String block = content.substring(codeStart, endIdx).trim();
                if (!block.isEmpty()) {
                    if (allCode.length() > 0) {
                        allCode.append("\n\n");
                    }
                    allCode.append(block);
                }
                searchStart = endIdx + 3;
            } else {
                break;
            }
        }
        
        // If we found code blocks, return concatenated result
        if (allCode.length() > 0) {
            return allCode.toString();
        }
        
        // Fallback: return the content as-is
        return content.trim();
    }
    
    /**
     * Detects if the Java code is a complete Spoon class (has main method, AbstractProcessor, etc.)
     * or just transformation rules.
     */
    private static boolean isCompleteSpoonClass(String javaCode) {
        if (javaCode == null || javaCode.isBlank()) {
            return false;
        }
        
        String code = javaCode.toLowerCase();
        // Check for indicators of a complete class:
        // - Has "public class" declaration
        // - Has "public static void main"
        // - Has "abstractprocessor" (typical for complete Spoon classes)
        boolean hasPublicClass = code.contains("public class");
        boolean hasMainMethod = code.contains("public static void main");
        boolean hasProcessor = code.contains("abstractprocessor") || code.contains("extends abstractprocessor");
        
        // A complete class should have all three
        return hasPublicClass && hasMainMethod && hasProcessor;
    }
    
    /**
     * Extracts the class name from Java code.
     * Looks for "public class ClassName" pattern.
     */
    private static String extractClassName(String javaCode) {
        if (javaCode == null || javaCode.isBlank()) {
            return "SpoonApplyRules";
        }
        
        // Pattern: "public class ClassName" or "public class ClassName {"
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
            "public\\s+class\\s+([A-Za-z_][A-Za-z0-9_]*)"
        );
        java.util.regex.Matcher matcher = pattern.matcher(javaCode);
        if (matcher.find()) {
            return matcher.group(1);
        }
        
        return "SpoonApplyRules";
    }
    
    /**
     * Adapts a complete Spoon class from LLM output to work with our paths.
     * Replaces input/output paths in the main method to point to the correct locations.
     * 
     * Input: the original source file to transform
     * Output: directory where transformed files will be written (Spoon maintains package structure)
     * 
     * This method handles multiple patterns from different prompt formatters:
     * - Prompt5FilePromptFormatter: args.length > 0 ? args[0] : "..."
     * - Prompt4FilePromptFormatter: direct string assignment
     * - V2InContextFilePromptFormatter: direct addInputResource("src/main/java")
     * - Any other variations the LLM might generate
     */
    private static String adaptCompleteClass(String javaCode, Path originalSourceFile, Path commitReportDir) {
        if (javaCode == null || javaCode.isBlank()) {
            return javaCode;
        }
        
        Path outputDir = commitReportDir.resolve("transformed");
        String outputDirPath = outputDir.toAbsolutePath().toString().replace("\\", "/");
        String inputPath = originalSourceFile.toAbsolutePath().toString().replace("\\", "/");
        
        String adapted = javaCode;
        
        // ===== INPUT PATH REPLACEMENTS =====
        
        // Pattern 1: String inputPath = args.length > 0 ? args[0] : "...";
        // Handle variations with different spacing
        adapted = adapted.replaceAll(
            "(String\\s+inputPath\\s*=\\s*)(args\\s*\\.\\s*length\\s*>\\s*0\\s*\\?\\s*args\\s*\\[\\s*0\\s*\\]\\s*:\\s*\")([^\"]+)(\"\\s*;)",
            "$1\"" + inputPath + "\";"
        );
        
        // Pattern 2: String inputPath = "..."; (simple assignment)
        adapted = adapted.replaceAll(
            "(String\\s+inputPath\\s*=\\s*\")([^\"]+)(\"\\s*;)",
            "$1" + inputPath + "$3"
        );
        
        // Pattern 3: addInputResource("...") with any path
        // This catches V2InContextFilePromptFormatter pattern: addInputResource("src/main/java")
        adapted = adapted.replaceAll(
            "(addInputResource\\s*\\(\\s*\")([^\"]+)(\"\\s*\\))",
            "$1" + inputPath + "$3"
        );
        
        // Pattern 4: addInputResource(inputPath) - variable reference
        adapted = adapted.replaceAll(
            "(addInputResource\\s*\\(\\s*)inputPath(\\s*\\))",
            "$1\"" + inputPath + "\"$2"
        );
        
        // Pattern 5: addInputResource(new File("..."))
        adapted = adapted.replaceAll(
            "(addInputResource\\s*\\(\\s*new\\s+File\\s*\\(\\s*\")([^\"]+)(\"\\s*\\)\\s*\\))",
            "$1" + inputPath + "$3"
        );
        
        // ===== OUTPUT PATH REPLACEMENTS =====
        
        // Pattern 1: String outputPath = args.length > 1 ? args[1] : "...";
        adapted = adapted.replaceAll(
            "(String\\s+outputPath\\s*=\\s*)(args\\s*\\.\\s*length\\s*>\\s*1\\s*\\?\\s*args\\s*\\[\\s*1\\s*\\]\\s*:\\s*\")([^\"]+)(\"\\s*;)",
            "$1\"" + outputDirPath + "\";"
        );
        
        // Pattern 2: String outputPath = "..."; (simple assignment)
        adapted = adapted.replaceAll(
            "(String\\s+outputPath\\s*=\\s*\")([^\"]+)(\"\\s*;)",
            "$1" + outputDirPath + "$3"
        );
        
        // Pattern 3: setSourceOutputDirectory("...")
        adapted = adapted.replaceAll(
            "(setSourceOutputDirectory\\s*\\(\\s*\")([^\"]+)(\"\\s*\\))",
            "$1" + outputDirPath + "$3"
        );
        
        // Pattern 4: setSourceOutputDirectory(outputPath) - variable reference
        adapted = adapted.replaceAll(
            "(setSourceOutputDirectory\\s*\\(\\s*)outputPath(\\s*\\))",
            "$1\"" + outputDirPath + "\"$2"
        );
        
        // Pattern 5: setSourceOutputDirectory(new File("..."))
        adapted = adapted.replaceAll(
            "(setSourceOutputDirectory\\s*\\(\\s*new\\s+File\\s*\\(\\s*\")([^\"]+)(\"\\s*\\)\\s*\\))",
            "$1" + outputDirPath + "$3"
        );
        
        // Pattern 6: setSourceOutputDirectory(new File(outputPath))
        adapted = adapted.replaceAll(
            "(setSourceOutputDirectory\\s*\\(\\s*new\\s+File\\s*\\(\\s*)outputPath(\\s*\\)\\s*\\))",
            "$1\"" + outputDirPath + "\"$2"
        );
        
        // Pattern 7: Standalone File constructor (if used for output)
        // Only replace if it's clearly an output-related File (be conservative)
        // This is a fallback for edge cases
        
        return adapted;
    }
    
    /**
     * Indents code and adapts it to use the factory parameter instead of getFactory().
     * Note: Rules are copied as-is from LLM output, no modifications are made.
     */
    private static String indentAndAdaptCode(String code, String indent) {
        if (code == null || code.isBlank()) {
            return indent + "// No code provided" + System.lineSeparator();
        }
        
        String nl = System.lineSeparator();
        StringBuilder result = new StringBuilder();
        String[] lines = code.split("\\r?\\n");
        
        for (String line : lines) {
            String trimmed = line.trim();
            // Skip empty lines or preserve them
            if (trimmed.isEmpty()) {
                result.append(indent).append(nl);
            } else {
                // Replace getFactory() with factory parameter (only this adaptation is needed)
                String adapted = trimmed.replace("getFactory()", "factory");
                // Rules are copied as-is from LLM - no other modifications
                result.append(indent).append(adapted).append(nl);
            }
        }
        
        return result.toString();
    }
}


