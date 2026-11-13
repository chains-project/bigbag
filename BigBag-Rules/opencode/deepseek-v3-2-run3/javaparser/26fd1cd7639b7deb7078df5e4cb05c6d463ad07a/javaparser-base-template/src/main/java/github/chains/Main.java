package github.chains;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

public class Main {
    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            System.err.println("Usage: java github.chains.Main <source-directory>");
            System.exit(1);
        }
        
        Path sourceDir = Paths.get(args[0]);
        if (!Files.exists(sourceDir) || !Files.isDirectory(sourceDir)) {
            System.err.println("Source directory does not exist: " + sourceDir);
            System.exit(1);
        }
        
        Files.walk(sourceDir)
            .filter(p -> p.toString().endsWith(".java"))
            .forEach(Main::transformFile);
        
        System.out.println("Transformation completed successfully.");
    }
    
    private static void transformFile(Path file) {
        try {
            List<String> lines = Files.readAllLines(file);
            boolean[] modified = new boolean[]{false};
            
            // Transformations
            List<String> transformed = lines.stream()
                .map(line -> {
                    String originalLine = line;
                    
                    // Remove import of com.gargoylesoftware.htmlunit.ScriptResult
                    if (line.trim().startsWith("import com.gargoylesoftware.htmlunit.ScriptResult")) {
                        modified[0] = true;
                        return "";
                    }
                    
                    // Transform: ScriptResult scriptResult = new ScriptResult(result);
                    // To: Object scriptResult = result;
                    if (line.contains("new ScriptResult(") && line.contains("ScriptResult")) {
                        modified[0] = true;
                        String arg = extractArgument(line, "new ScriptResult(");
                        line = line.replace("new ScriptResult(" + arg + ")", arg);
                        line = line.replace("ScriptResult ", "Object ");
                    }
                    
                    // Transform: .getJavaScriptResult()
                    // Remove method call since result is already the JavaScript result
                    if (line.contains(".getJavaScriptResult()")) {
                        modified[0] = true;
                        line = line.replace(".getJavaScriptResult()", "");
                    }
                    
                    return line;
                })
                .collect(Collectors.toList());
            
            // Remove empty lines from removed imports
            transformed = transformed.stream()
                .filter(line -> !line.isEmpty())
                .collect(Collectors.toList());
            
            if (modified[0]) {
                Files.write(file, transformed);
                System.out.println("Modified: " + file);
            }
        } catch (Exception e) {
            System.err.println("Error processing " + file + ": " + e.getMessage());
        }
    }
    
    private static String extractArgument(String line, String prefix) {
        int start = line.indexOf(prefix) + prefix.length();
        int end = line.indexOf(")", start);
        if (end == -1) return "";
        
        // Count parentheses to handle nested calls
        int parenCount = 1;
        for (int i = start; i < line.length(); i++) {
            if (line.charAt(i) == '(') parenCount++;
            else if (line.charAt(i) == ')') {
                parenCount--;
                if (parenCount == 0) {
                    end = i;
                    break;
                }
            }
        }
        
        return line.substring(start, end).trim();
    }
}