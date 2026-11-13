package github.chains;

import java.io.*;
import java.nio.file.*;

/**
 * Simple transformation to fix the ScriptResult class issue in ChartUtil.java
 * This is a direct fix for the specific problem reported.
 */
public class Main {
    public static void main(String[] args) {
        try {
            // Read the source file
            Path sourcePath = Paths.get("/workspace/code-coverage-api-plugin/ui-tests/src/main/java/io/jenkins/plugins/coverage/util/ChartUtil.java");
            String content = new String(Files.readAllBytes(sourcePath));
            
            // Remove the unused import statement
            content = content.replaceAll(
                "import com.gargoylesoftware.htmlunit.ScriptResult;\\s*", ""
            );
            
            // Fix the first occurrence: ScriptResult scriptResult = new ScriptResult(result);
            content = content.replaceAll(
                "ScriptResult scriptResult = new ScriptResult\\(result\\);",
                "Object scriptResult = result;"
            );
            
            // Fix the second occurrence: new ScriptResult(result).getJavaScriptResult()
            content = content.replaceAll(
                "new ScriptResult\\(result\\)\\.getJavaScriptResult\\(\\)",
                "result"
            );
            
            // Fix the third occurrence: scriptResult.getJavaScriptResult().toString()
            content = content.replaceAll(
                "scriptResult\\.getJavaScriptResult\\(\\)\\.toString\\(\\)",
                "scriptResult.toString()"
            );
            
            // Write back to the file
            Files.write(sourcePath, content.getBytes());
            
            System.out.println("Successfully fixed ChartUtil.java");
            System.out.println("The transformation replaced:");
            System.out.println("- ScriptResult scriptResult = new ScriptResult(result); with Object scriptResult = result;");
            System.out.println("- new ScriptResult(result).getJavaScriptResult() with result");
            System.out.println("- scriptResult.getJavaScriptResult().toString() with scriptResult.toString()");
            System.out.println("- Removed unused import com.gargoylesoftware.htmlunit.ScriptResult");
            
        } catch (IOException e) {
            System.err.println("Error processing file: " + e.getMessage());
            e.printStackTrace();
        }
    }
}