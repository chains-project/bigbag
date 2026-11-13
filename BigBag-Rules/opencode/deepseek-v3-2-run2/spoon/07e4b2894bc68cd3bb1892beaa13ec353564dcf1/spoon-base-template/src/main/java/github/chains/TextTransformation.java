package github.chains;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Simple text-based transformation to fix breaking dependency changes
 * where com.gargoylesoftware.htmlunit.ScriptResult is replaced by
 * org.jenkinsci.test.acceptance.plugins.scriptler.ScriptResult
 */
public class TextTransformation {
    
    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("Usage: java -jar text-transformation.jar <source-file>");
            System.exit(1);
        }
        
        String sourceFile = args[0];
        Path filePath = Paths.get(sourceFile);
        
        // Read the file content
        String content = Files.readString(filePath);
        
        // Apply transformations
        String transformedContent = transform(content);
        
        // Write back the transformed content
        Files.write(filePath, transformedContent.getBytes());
        
        System.out.println("Text transformation completed successfully!");
    }
    
    /**
     * Apply text transformations:
     * - Import: com.gargoylesoftware.htmlunit.ScriptResult -> org.jenkinsci.test.acceptance.plugins.scriptler.ScriptResult
     * - Constructor: new ScriptResult(Object) -> new ScriptResult(String)
     * - Method: getJavaScriptResult() -> toString()
     */
    private static String transform(String content) {
        // Replace import
        content = content.replace(
            "import com.gargoylesoftware.htmlunit.ScriptResult;",
            "import org.jenkinsci.test.acceptance.plugins.scriptler.ScriptResult;"
        );
        
        // Replace specific patterns one by one to avoid conflicts
        
        // Pattern 1: scriptResult.getJavaScriptResult().toString() -> scriptResult.toString()
        content = content.replaceAll(
            "(\\w+)\\.getJavaScriptResult\\(\\)\\.toString\\(\\)",
            "$1.toString()"
        );
        
        // Pattern 2: new ScriptResult(result).getJavaScriptResult() -> new ScriptResult(result.toString()).toString()
        content = content.replaceAll(
            "new ScriptResult\\(([^)]+)\\)\\.getJavaScriptResult\\(\\)",
            "new ScriptResult($1.toString()).toString()"
        );
        
        // Pattern 3: scriptResult.getJavaScriptResult() -> scriptResult.toString() (standalone)
        content = content.replaceAll(
            "(\\w+)\\.getJavaScriptResult\\(\\)",
            "$1.toString()"
        );
        
        // Pattern 4: new ScriptResult(result) -> new ScriptResult(result.toString()) (standalone)
        content = content.replaceAll(
            "new ScriptResult\\(([^)]+)\\)",
            "new ScriptResult($1.toString())"
        );
        
        return content;
    }
}