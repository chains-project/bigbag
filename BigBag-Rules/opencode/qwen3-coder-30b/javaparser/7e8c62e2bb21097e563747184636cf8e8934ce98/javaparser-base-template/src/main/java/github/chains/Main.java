package github.chains;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.regex.Pattern;

public class Main {
    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        processDirectory(new File(sourceDir));
    }
    
    private static void processDirectory(File dir) throws Exception {
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    processDirectory(file);
                } else if (file.getName().endsWith(".java")) {
                    processJavaFile(file);
                }
            }
        }
    }
    
    private static void processJavaFile(File file) throws Exception {
        // Read entire file content
        String content = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
        
        // Track changes
        boolean changed = false;
        String newContent = content;
        
        // Remove import statement for ScriptResult
        newContent = newContent.replaceAll(
            "import\\s+com\\.gargoylesoftware\\.htmlunit\\.ScriptResult;\\s*\\n", 
            ""
        );
        if (!newContent.equals(content)) {
            changed = true;
        }
        
        // Replace pattern: ScriptResult scriptResult = new ScriptResult(result);
        // With: Object scriptResult = result;
        newContent = newContent.replaceAll(
            "(\\s+)ScriptResult\\s+(\\w+)\\s*=\\s*new\\s+ScriptResult\\(([^;]+)\\);",
            "$1Object $2 = $3;"
        );
        
        // Replace pattern: new ScriptResult(result).getJavaScriptResult()
        // With: result
        newContent = newContent.replaceAll(
            "new\\s+ScriptResult\\(([^)]+)\\)\\.getJavaScriptResult\\(\\)",
            "$1"
        );
        
        // Handle the specific pattern in getDataOfOnlyChartOnPageWithGivenToolAttribute
        newContent = newContent.replaceAll(
            "Object\\s+scriptResult\\s*=\\s*new\\s+ScriptResult\\(([^)]+)\\)\\.getJavaScriptResult\\(\\)",
            "Object scriptResult = $1"
        );
        
        // If changes were made, write back to file
        if (changed || !newContent.equals(content)) {
            Files.write(file.toPath(), newContent.getBytes(StandardCharsets.UTF_8));
            System.out.println("Fixed: " + file.getAbsolutePath());
        }
    }
}