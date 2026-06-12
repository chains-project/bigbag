package github.chains;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.ArrayList;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

/**
 * Generic transformation rule for fixing the ScriptResult API breaking change
 * in acceptance-test-harness dependency.
 * 
 * Breaking Change Analysis:
 * - Old API: PageObject.executeScript() returned Object that needed to be wrapped in ScriptResult
 * - New API: PageObject.executeScript() directly returns the JavaScript result as Object
 * 
 * Transformation Patterns:
 * 1. Remove import: "import com.gargoylesoftware.htmlunit.ScriptResult;"
 * 2. Replace: "new ScriptResult(result).getJavaScriptResult()" with "result"
 * 3. Replace: "ScriptResult scriptResult = new ScriptResult(result); ... scriptResult.getJavaScriptResult()"
 *    with "Object scriptResult = result; ... scriptResult"
 * 
 * This transformation is generic and can be applied to any project affected by this
 * breaking change in the acceptance-test-harness dependency.
 */
public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source-directory>");
            System.err.println("Applies transformation for ScriptResult API breaking change");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Processing source directory: " + sourceDir);
        System.out.println("Transformation: Fixing ScriptResult API breaking change");
        System.out.println("Old API: new ScriptResult(executeScript(...)).getJavaScriptResult()");
        System.out.println("New API: executeScript(...) returns result directly");
        
        try {
            List<Path> javaFiles = findJavaFiles(sourceDir);
            int transformedFiles = 0;
            
            for (Path javaFile : javaFiles) {
                if (processJavaFile(javaFile)) {
                    transformedFiles++;
                }
            }
            
            System.out.println("Transformation completed. Processed " + transformedFiles + " file(s).");
            
        } catch (Exception e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static List<Path> findJavaFiles(String directory) throws Exception {
        List<Path> javaFiles = new ArrayList<>();
        Files.walk(Paths.get(directory))
            .filter(path -> path.toString().endsWith(".java"))
            .forEach(javaFiles::add);
        return javaFiles;
    }
    
    private static boolean processJavaFile(Path javaFile) throws Exception {
        String content = Files.readString(javaFile);
        String originalContent = content;
        
        // Pattern 1: Remove ScriptResult import
        content = content.replace("import com.gargoylesoftware.htmlunit.ScriptResult;", "");
        content = content.replace("import com.gargoylesoftware.htmlunit.ScriptResult;\n", "");
        content = content.replace("import com.gargoylesoftware.htmlunit.ScriptResult;\r\n", "");
        
        // Pattern 2: Replace new ScriptResult(...).getJavaScriptResult() with the argument
        // This handles inline usage like: new ScriptResult(result).getJavaScriptResult()
        Pattern pattern2 = Pattern.compile("new\\s+ScriptResult\\s*\\(\\s*([^)]+)\\s*\\)\\s*\\.\\s*getJavaScriptResult\\s*\\(\\s*\\)");
        Matcher matcher2 = pattern2.matcher(content);
        if (matcher2.find()) {
            content = matcher2.replaceAll("$1");
            System.out.println("  Fixed inline ScriptResult usage in: " + javaFile);
        }
        
        // Pattern 3: Replace variable declarations and their usage
        // This handles: ScriptResult var = new ScriptResult(expr); ... var.getJavaScriptResult()
        // We need to be more careful with this pattern as it requires understanding the scope
        // For simplicity, we'll use a multi-pass approach
        
        // First find all ScriptResult variable declarations
        Pattern varDeclPattern = Pattern.compile("(ScriptResult\\s+(\\w+)\\s*=\\s*new\\s+ScriptResult\\s*\\(\\s*([^)]+)\\s*\\)\\s*;)");
        Matcher varDeclMatcher = varDeclPattern.matcher(content);
        
        while (varDeclMatcher.find()) {
            String varName = varDeclMatcher.group(2);
            String expr = varDeclMatcher.group(3);
            String fullMatch = varDeclMatcher.group(1);
            
            // Replace the declaration: ScriptResult var = new ScriptResult(expr); -> Object var = expr;
            String replacement = "Object " + varName + " = " + expr + ";";
            content = content.replace(fullMatch, replacement);
            
            // Now replace all occurrences of var.getJavaScriptResult() with just var
            Pattern varUsagePattern = Pattern.compile("\\b" + Pattern.quote(varName) + "\\s*\\.\\s*getJavaScriptResult\\s*\\(\\s*\\)");
            content = varUsagePattern.matcher(content).replaceAll(varName);
            
            System.out.println("  Fixed ScriptResult variable '" + varName + "' in: " + javaFile);
        }
        
        // Also handle the case where the variable type is fully qualified
        Pattern varDeclPattern2 = Pattern.compile("(com\\.gargoylesoftware\\.htmlunit\\.ScriptResult\\s+(\\w+)\\s*=\\s*new\\s+ScriptResult\\s*\\(\\s*([^)]+)\\s*\\)\\s*;)");
        Matcher varDeclMatcher2 = varDeclPattern2.matcher(content);
        
        while (varDeclMatcher2.find()) {
            String varName = varDeclMatcher2.group(2);
            String expr = varDeclMatcher2.group(3);
            String fullMatch = varDeclMatcher2.group(1);
            
            // Replace the declaration
            String replacement = "Object " + varName + " = " + expr + ";";
            content = content.replace(fullMatch, replacement);
            
            // Replace usages
            Pattern varUsagePattern = Pattern.compile("\\b" + Pattern.quote(varName) + "\\s*\\.\\s*getJavaScriptResult\\s*\\(\\s*\\)");
            content = varUsagePattern.matcher(content).replaceAll(varName);
            
            System.out.println("  Fixed fully-qualified ScriptResult variable '" + varName + "' in: " + javaFile);
        }
        
        // Remove empty import lines that might have been created
        content = content.replaceAll("(?m)^\\s*import\\s*;\\s*$", "");
        
        if (!content.equals(originalContent)) {
            Files.writeString(javaFile, content);
            return true;
        }
        
        return false;
    }
}