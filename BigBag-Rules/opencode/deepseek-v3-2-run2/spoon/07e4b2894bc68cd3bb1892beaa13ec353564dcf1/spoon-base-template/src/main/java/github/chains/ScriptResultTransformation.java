package github.chains;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * Generic transformation to fix breaking dependency changes involving ScriptResult class replacement.
 * 
 * This transformation handles the replacement of:
 * - Old import: com.gargoylesoftware.htmlunit.ScriptResult
 * - New import: org.jenkinsci.test.acceptance.plugins.scriptler.ScriptResult
 * - Constructor change: new ScriptResult(Object) → new ScriptResult(String)
 * - Method change: getJavaScriptResult() → toString()
 * 
 * Usage:
 * java -cp spoon-base-template.jar github.chains.ScriptResultTransformation <source-directory>
 */
public class ScriptResultTransformation {
    
    private static final String OLD_IMPORT = "com.gargoylesoftware.htmlunit.ScriptResult";
    private static final String NEW_IMPORT = "org.jenkinsci.test.acceptance.plugins.scriptler.ScriptResult";
    
    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Usage: java github.chains.ScriptResultTransformation <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Applying ScriptResult transformation to: " + sourceDir);
        
        try {
            transformDirectory(Paths.get(sourceDir));
            System.out.println("Transformation completed successfully");
        } catch (IOException e) {
            System.err.println("Error during transformation: " + e.getMessage());
            System.exit(1);
        }
    }
    
    private static void transformDirectory(Path directory) throws IOException {
        Files.walk(directory)
            .filter(path -> path.toString().endsWith(".java"))
            .forEach(ScriptResultTransformation::transformFile);
    }
    
    private static void transformFile(Path filePath) {
        try {
            List<String> lines = Files.readAllLines(filePath);
            boolean modified = false;
            
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                
                // Replace import
                if (line.contains(OLD_IMPORT)) {
                    lines.set(i, line.replace(OLD_IMPORT, NEW_IMPORT));
                    modified = true;
                    System.out.println("Replaced import in: " + filePath);
                }
                
                // Replace method: getJavaScriptResult() → toString()
                // Apply this first to avoid conflicts with constructor replacement
                if (line.contains(".getJavaScriptResult()")) {
                    lines.set(i, line.replace(".getJavaScriptResult()", ".toString()"));
                    modified = true;
                    System.out.println("Replaced method in: " + filePath);
                }
                
                // Replace constructor: new ScriptResult(Object) → new ScriptResult(String)
                // Only apply when not already calling toString() and not already a string literal
                if (line.contains("new ScriptResult(") && !line.contains("new ScriptResult(\"") && !line.contains(".toString()")) {
                    String newLine = line.replaceAll("new ScriptResult\\(([^)]+)\\)", "new ScriptResult($1.toString())");
                    if (!newLine.equals(line)) {
                        lines.set(i, newLine);
                        modified = true;
                        System.out.println("Replaced constructor in: " + filePath);
                    }
                }
                
                // Replace method: getJavaScriptResult() → toString()
                if (line.contains(".getJavaScriptResult()")) {
                    lines.set(i, line.replace(".getJavaScriptResult()", ".toString()"));
                    modified = true;
                    System.out.println("Replaced method in: " + filePath);
                }
            }
            
            if (modified) {
                Files.write(filePath, lines);
                System.out.println("Successfully transformed: " + filePath);
            }
            
        } catch (IOException e) {
            System.err.println("Error transforming file " + filePath + ": " + e.getMessage());
        }
    }
}