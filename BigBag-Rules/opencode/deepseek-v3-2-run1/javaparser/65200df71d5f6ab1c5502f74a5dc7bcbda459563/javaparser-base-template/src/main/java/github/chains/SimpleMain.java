package github.chains;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class SimpleMain {
    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("Usage: java SimpleMain <source-directory>");
            System.exit(1);
        }
        
        Path sourceDir = Paths.get(args[0]);
        Files.walk(sourceDir)
            .filter(p -> p.toString().endsWith(".java"))
            .forEach(SimpleMain::processFile);
        
        System.out.println("Transformation completed.");
    }
    
    private static void processFile(Path filePath) {
        try {
            String content = new String(Files.readAllBytes(filePath));
            String original = content;
            
            // Pattern 1: AnalysisEngineConfiguration.builder().addEnabledLanguages(...).nextMethod(...)
            // Replace with: AnalysisEngineConfiguration.builder().nextMethod(...)
            content = content.replaceAll(
                "AnalysisEngineConfiguration\\.builder\\(\\)\\.addEnabledLanguages\\([^)]+\\)\\.([a-zA-Z])",
                "AnalysisEngineConfiguration.builder().$1"
            );
            
            // Pattern 2: AnalysisEngineConfiguration.builder()\n        .addEnabledLanguages(...)\n        .nextMethod(...)
            // Replace with: AnalysisEngineConfiguration.builder()\n        .nextMethod(...)
            content = content.replaceAll(
                "AnalysisEngineConfiguration\\.builder\\(\\)\\s*\\.\\s*addEnabledLanguages\\([^)]+\\)\\s*\\.\\s*([a-zA-Z])",
                "AnalysisEngineConfiguration.builder().$1"
            );
            
            // Pattern 3: .addEnabledLanguages(...)\n        .nextMethod(...)
            // When addEnabledLanguages is in a chain with line breaks
            content = content.replaceAll(
                "\\.\\s*addEnabledLanguages\\([^)]+\\)\\s*\\.\\s*([a-zA-Z])",
                ".$1"
            );
            
            if (!content.equals(original)) {
                Files.write(filePath, content.getBytes());
                System.out.println("Modified: " + filePath);
            }
        } catch (Exception e) {
            System.err.println("Error processing " + filePath + ": " + e.getMessage());
        }
    }
}