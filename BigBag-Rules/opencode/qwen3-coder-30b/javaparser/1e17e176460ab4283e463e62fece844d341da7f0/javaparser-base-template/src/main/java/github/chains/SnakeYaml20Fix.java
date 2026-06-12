package github.chains;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Simple tool to fix SnakeYAML 2.0 breaking changes in Maven projects
 * Specifically targets the pattern: new Yaml(constructor, new Representer(), new DumperOptions(), new ModelResolver())
 * And converts it to: new Yaml(constructor)
 * Also handles Serializer constructor patterns
 */
public class SnakeYaml20Fix {
    public static void main(String[] args) {
        try {
            String sourceDir = args.length > 0 ? args[0] : "/workspace/polyglot-maven";
            processDirectory(sourceDir);
            System.out.println("SnakeYAML 2.0 transformation completed successfully!");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void processDirectory(String dirPath) throws IOException {
        Files.walk(Paths.get(dirPath))
                .filter(path -> path.toString().endsWith(".java"))
                .forEach(path -> {
                    try {
                        processJavaFile(path.toString());
                    } catch (IOException e) {
                        System.err.println("Error processing " + path + ": " + e.getMessage());
                    }
                });
    }

    private static void processJavaFile(String filePath) throws IOException {
        String content = new String(Files.readAllBytes(Paths.get(filePath)));
        String fixedContent = fixSnakeYamlCalls(content);
        
        if (!content.equals(fixedContent)) {
            try (FileWriter writer = new FileWriter(filePath)) {
                writer.write(fixedContent);
            }
            System.out.println("Fixed: " + filePath);
        }
    }

    private static String fixSnakeYamlCalls(String content) {
        String originalContent = content;
        
        // Pattern 1: Fix Yaml constructor calls with 4 arguments
        // This pattern looks for: new Yaml(..., new Representer(), new DumperOptions(), new ModelResolver())
        Pattern pattern1 = Pattern.compile(
            "(new\\s+Yaml\\([^)]*)\\s*,\\s*new\\s+Representer\\(\\)\\s*,\\s*new\\s+DumperOptions\\(\\)\\s*,\\s*new\\s+ModelResolver\\(\\)\\s*\\)",
            Pattern.MULTILINE
        );
        
        // Replace with: new Yaml(...)
        // Keeping only the first argument (constructor)
        Matcher matcher1 = pattern1.matcher(content);
        StringBuffer sb1 = new StringBuffer();
        
        while (matcher1.find()) {
            String replacement = matcher1.group(1) + ")";
            matcher1.appendReplacement(sb1, replacement);
        }
        matcher1.appendTail(sb1);
        content = sb1.toString();
        
        // Pattern 2: Fix Serializer constructor calls with 4 arguments  
        // This pattern looks for: new Serializer(..., new ModelResolver(), ..., ...)
        Pattern pattern2 = Pattern.compile(
            "(new\\s+Serializer\\([^)]*)\\s*,\\s*new\\s+ModelResolver\\(\\)\\s*,[^)]*\\)",
            Pattern.MULTILINE
        );
        
        // Replace with: new Serializer(...)
        // Keeping only the first argument (the emitter)
        Matcher matcher2 = pattern2.matcher(content);
        StringBuffer sb2 = new StringBuffer();
        
        while (matcher2.find()) {
            String replacement = matcher2.group(1) + ")";
            matcher2.appendReplacement(sb2, replacement);
        }
        matcher2.appendTail(sb2);
        content = sb2.toString();
        
        return content;
    }
}