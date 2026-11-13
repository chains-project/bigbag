package github.chains;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

/**
 * Generic transformation rule for the SonarLint API breaking change:
 * AnalysisEngineConfiguration.Builder.addEnabledLanguages(Set<Language>) removed.
 * 
 * This rule demonstrates the transformation pattern without actually modifying files.
 */
public class Main {
    
    private static final String BUILDER_TYPE = "org.sonarsource.sonarlint.core.analysis.api.AnalysisEngineConfiguration.Builder";
    private static final String LANGUAGE_TYPE = "org.sonarsource.sonarlint.core.commons.Language";
    
    // Pattern to match: AnalysisEngineConfiguration.builder().addEnabledLanguages(...)
    private static final Pattern OLD_PATTERN = Pattern.compile(
        "(\\s*)(AnalysisEngineConfiguration\\.builder\\(\\)|" + 
        Pattern.quote(BUILDER_TYPE.replace(".", "\\.")) + "\\s+\\w+\\s*=\\s*AnalysisEngineConfiguration\\.builder\\(\\))" +
        "\\.addEnabledLanguages\\(([^)]+)\\)"
    );
    
    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("Usage: java github.chains.Main <source_directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Analyzing directory: " + sourceDir);
        
        // Walk through Java files
        Files.walk(Paths.get(sourceDir))
            .filter(path -> path.toString().endsWith(".java"))
            .forEach(path -> analyzeFile(path));
        
        System.out.println("\n=== GENERIC TRANSFORMATION RULE ===");
        System.out.println("Breaking Change: " + BUILDER_TYPE + ".addEnabledLanguages(Set<" + LANGUAGE_TYPE + ">) has been removed");
        System.out.println("\nOld API Pattern:");
        System.out.println("  " + BUILDER_TYPE + " builder = AnalysisEngineConfiguration.builder();");
        System.out.println("  builder.addEnabledLanguages(languagesSet); // or chained: builder().addEnabledLanguages(...)...");
        System.out.println("\nNew API Pattern:");
        System.out.println("  " + BUILDER_TYPE + " builder = AnalysisEngineConfiguration.builder();");
        System.out.println("  for (" + LANGUAGE_TYPE + " language : languagesSet) {");
        System.out.println("    builder.addEnabledLanguage(language);");
        System.out.println("  }");
        System.out.println("\nTransformation Steps:");
        System.out.println("1. Extract builder expression (e.g., AnalysisEngineConfiguration.builder())");
        System.out.println("2. Create builder variable");
        System.out.println("3. Replace addEnabledLanguages(set) with for-each loop");
        System.out.println("4. Continue method chain on builder variable");
        System.out.println("\n=== END TRANSFORMATION RULE ===");
    }
    
    private static void analyzeFile(Path path) {
        try {
            String content = Files.readString(path);
            // Simple regex search for the pattern
            if (content.contains("addEnabledLanguages")) {
                System.out.println("\nFound in: " + path);
                System.out.println("File likely contains the old API pattern.");
                System.out.println("Manual inspection needed to apply transformation.");
            }
        } catch (IOException e) {
            System.err.println("Error reading file: " + path);
        }
    }
}