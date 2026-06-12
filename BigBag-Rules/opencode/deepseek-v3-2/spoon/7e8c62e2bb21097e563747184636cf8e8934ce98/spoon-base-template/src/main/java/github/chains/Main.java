package github.chains;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
/**
 * A generic transformation rule for fixing breaking API changes in acceptance-test-harness.
 * 
 * Breaking Change Pattern:
 * - Old: com.gargoylesoftware.htmlunit.ScriptResult was used to wrap executeScript() results
 * - New: executeScript() returns Object directly, ScriptResult class removed
 * 
 * Transformation Rules:
 * 1. Remove import com.gargoylesoftware.htmlunit.ScriptResult
 * 2. Replace result with result
 * 3. Replace  scriptResult with result
 * 4. Handle null checks appropriately
 */
public class Main {
    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("Usage: java github.chains.Main <source-directory>");
            System.err.println("Applies transformation for breaking API change: com.gargoylesoftware.htmlunit.ScriptResult removal");
            System.exit(1);
        }
        String sourceDir = args[0];
        System.out.println("Applying transformation to: " + sourceDir);
        // Find all Java files
        try (Stream<Path> paths = Files.walk(Paths.get(sourceDir))) {
            List<Path> javaFiles = paths
                .filter(Files::isRegularFile)
                .filter(p -> p.toString().endsWith(".java"))
                .collect(Collectors.toList());
            System.out.println("Found " + javaFiles.size() + " Java files");
            int transformedFiles = 0;
            for (Path javaFile : javaFiles) {
                if (transformFile(javaFile)) {
                    transformedFiles++;
                }
            }
            System.out.println("Transformed " + transformedFiles + " files");
        }
    }
    private static boolean transformFile(Path javaFile) throws IOException {
        String content = Files.readString(javaFile);
        String originalContent = content;
        // Rule 1: Remove import
        content = content.replace(
            "",
            ""
        );
        content = content.replace(
            "\n",
            ""
        );
        content = content.replace(
            "\n",
            ""
        );
        // Rule 2: Replace result with result
        content = content.replace(
            "result",
            "result"
        );
        // Rule 3: Replace  ... scriptResult
        // This is more complex and would need AST transformation
        // For now, we handle common patterns
        // Pattern: return result != null ? result.toString() : null;
        content = content.replaceAll(
            "ScriptResult\\s+([a-zA-Z_$][a-zA-Z0-9_$]*)\\s*=\\s*new\\s+ScriptResult\\(([^)]+)\\)\\s*;\\s*return\\s+\\1\\.getJavaScriptResult\\(\\)\\.toString\\(\\)",
            "return $2 != null ? $2.toString() : null"
        );
        // Pattern:  ... scriptResult
        content = content.replaceAll(
            "ScriptResult\\s+([a-zA-Z_$][a-zA-Z0-9_$]*)\\s*=\\s*new\\s+ScriptResult\\(([^)]+)\\)\\s*;",
            ""
        );
        content = content.replaceAll(
            "\\b([a-zA-Z_$][a-zA-Z0-9_$]*)\\.getJavaScriptResult\\(\\)",
            "$1"
        );
        // Remove empty lines caused by removal
        content = content.replaceAll("\n\\s*\n", "\n");
        if (!content.equals(originalContent)) {
            Files.writeString(javaFile, content);
            System.out.println("Transformed: " + javaFile);
            return true;
        }
        return false;
    }
}