package github.chains;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.regex.*;

public class Main {
    public static void main(String[] args) throws IOException {
        // Process the specific file that has the ScriptResult issue
        String targetFile = "/workspace/code-coverage-api-plugin/ui-tests/src/main/java/io/jenkins/plugins/coverage/util/ChartUtil.java";
        
        processFile(Paths.get(targetFile));
        System.out.println("Transformation completed.");
    }
    
    private static void processFile(Path file) {
        try {
            // Read the file content
            String content = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
            boolean modified = false;
            
            // Remove the import statement for ScriptResult
            content = content.replaceAll("(?m)^import\\s+com\\.gargoylesoftware\\.htmlunit\\.ScriptResult;\\s*$", "");
            modified = true;
            
            // Replace the ScriptResult usage in getChartDataById method
            // From: ScriptResult scriptResult = new ScriptResult(result);
            // To: Object scriptResult = result;
            content = content.replaceAll(
                "ScriptResult\\s+(\\w+)\\s*=\\s*new\\s+ScriptResult\\s*\\(\\s*(\\w+)\\s*\\)\\s*;",
                "Object $1 = $2;"
            );
            modified = true;
            
            // Replace getJavaScriptResult() calls in the first method
            // From: scriptResult.getJavaScriptResult().toString()
            // To: scriptResult.toString()
            content = content.replaceAll(
                "scriptResult\\.getJavaScriptResult\\(\\)\\.toString\\(\\)",
                "scriptResult.toString()"
            );
            modified = true;
            
            // Replace getJavaScriptResult() calls in the second method
            // From: new ScriptResult(result).getJavaScriptResult()
            // To: result
            content = content.replaceAll(
                "new\\s+ScriptResult\\s*\\(\\s*(\\w+)\\s*\\)\\.getJavaScriptResult\\(\\)",
                "$1"
            );
            modified = true;
            
            // Replace getJavaScriptResult() calls in the second method (with toString)
            // From: new ScriptResult(result).getJavaScriptResult().toString()
            // To: result.toString()
            content = content.replaceAll(
                "new\\s+ScriptResult\\s*\\(\\s*(\\w+)\\s*\\)\\.getJavaScriptResult\\(\\)\\.toString\\(\\)",
                "$1.toString()"
            );
            modified = true;
            
            // Write back if modified
            if (modified) {
                Files.write(file, content.getBytes(StandardCharsets.UTF_8));
                System.out.println("Modified: " + file);
            }
        } catch (IOException e) {
            System.err.println("Error processing " + file + ": " + e.getMessage());
        }
    }
}