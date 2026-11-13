package github.chains;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Pattern;

/**
 * Generic transformation rule for fixing sonarlint-core breaking API changes.
 * Specifically addresses the removal of addEnabledLanguages method in AnalysisEngineConfiguration.Builder.
 * 
 * Pattern to identify: 
 * AnalysisEngineConfiguration.builder().addEnabledLanguages(...)
 * 
 * This pattern needs to be replaced with a compatible API call for sonarlint-core 8.19.0+
 */
public class Main {
    
    public static void main(String[] args) throws IOException {
        String sourceDirectory = args.length > 0 ? args[0] : "/workspace/sorald";
        
        // Process all Java files in the directory recursively
        Files.walk(Paths.get(sourceDirectory))
                .filter(path -> path.toString().endsWith(".java"))
                .forEach(Main::processFile);
    }
    
    private static void processFile(Path filePath) {
        try {
            String content = Files.readString(filePath);
            String modifiedContent = identifyAndTransform(content);
            
            if (!modifiedContent.equals(content)) {
                // In a real implementation, we would write back to the file
                System.out.println("Would modify: " + filePath);
                System.out.println("Changes needed for sonarlint-core API compatibility");
            } else {
                System.out.println("No changes needed: " + filePath);
            }
        } catch (Exception e) {
            System.err.println("Error processing " + filePath + ": " + e.getMessage());
        }
    }
    
    /**
     * Identifies and transforms the problematic API call pattern
     */
    private static String identifyAndTransform(String content) {
        // Regex pattern to find the problematic call
        // This looks for the specific pattern that causes the compilation error
        Pattern pattern = Pattern.compile(
            "AnalysisEngineConfiguration\\.builder\\(\\)\\.addEnabledLanguages\\([^)]*\\)", 
            Pattern.MULTILINE);
        
        if (pattern.matcher(content).find()) {
            // In a real implementation, we would do the transformation here
            // For now, we just indicate that fixing is needed
            return content.replaceFirst(
                "AnalysisEngineConfiguration\\.builder\\(\\)\\.addEnabledLanguages\\([^)]*\\)",
                "/* TODO: Fix addEnabledLanguages - API changed in sonarlint-core 8.19.0 */");
        }
        
        return content;
    }
}
