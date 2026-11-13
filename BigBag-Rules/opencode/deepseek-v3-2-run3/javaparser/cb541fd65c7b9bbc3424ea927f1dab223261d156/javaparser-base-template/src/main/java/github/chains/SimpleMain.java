package github.chains;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

/**
 * Text-based transformation for hamcrest 2.2 breaking change.
 * Handles common method migrations from Matchers to other classes.
 */
public class SimpleMain {
    // Method migration mappings: old usage -> new usage
    private static final Map<String, String> METHOD_MAPPINGS = new HashMap<>();
    
    static {
        // Common method migrations from Matchers to CoreMatchers
        METHOD_MAPPINGS.put("Matchers.hasItems(", "CoreMatchers.hasItems(");
        METHOD_MAPPINGS.put("Matchers.notNullValue()", "CoreMatchers.notNullValue()");
        METHOD_MAPPINGS.put("Matchers.not(", "CoreMatchers.not(");
        METHOD_MAPPINGS.put("Matchers.containsString(", "CoreMatchers.containsString(");
        METHOD_MAPPINGS.put("Matchers.hasItem(", "CoreMatchers.hasItem(");
        METHOD_MAPPINGS.put("Matchers.equalTo(", "CoreMatchers.equalTo(");
        METHOD_MAPPINGS.put("Matchers.is(", "CoreMatchers.is(");
        METHOD_MAPPINGS.put("Matchers.allOf(", "CoreMatchers.allOf(");
        METHOD_MAPPINGS.put("Matchers.anyOf(", "CoreMatchers.anyOf(");
        METHOD_MAPPINGS.put("Matchers.hasSize(", "CoreMatchers.hasSize(");
        METHOD_MAPPINGS.put("Matchers.hasEntry(", "CoreMatchers.hasEntry(");
        METHOD_MAPPINGS.put("Matchers.hasProperty(", "CoreMatchers.hasProperty(");
        METHOD_MAPPINGS.put("Matchers.startsWith(", "CoreMatchers.startsWith(");
        METHOD_MAPPINGS.put("Matchers.endsWith(", "CoreMatchers.endsWith(");
        METHOD_MAPPINGS.put("Matchers.emptyIterableOf(", "CoreMatchers.emptyIterable(");
    }
    
    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("Usage: java github.chains.SimpleMain <source-directory>");
            System.exit(1);
        }
        
        Path sourceDir = Paths.get(args[0]);
        if (!Files.exists(sourceDir) || !Files.isDirectory(sourceDir)) {
            System.err.println("Error: Source directory does not exist: " + sourceDir);
            System.exit(1);
        }
        
        System.out.println("Transforming hamcrest Matchers usage...");
        transformDirectory(sourceDir);
        System.out.println("Transformation complete.");
    }
    
    private static void transformDirectory(Path dir) throws IOException {
        Files.walk(dir)
            .filter(p -> Files.isRegularFile(p) && p.toString().endsWith(".java"))
            .forEach(SimpleMain::transformFile);
    }
    
    private static void transformFile(Path file) {
        try {
            String content = Files.readString(file);
            String original = content;
            
            // First, fix imports
            content = content.replace("import org.hamcrest.Matchers;", "import org.hamcrest.CoreMatchers;");
            content = content.replace("import static org.hamcrest.Matchers.*;", "import static org.hamcrest.CoreMatchers.*;");
            
            // Apply method migrations
            for (Map.Entry<String, String> entry : METHOD_MAPPINGS.entrySet()) {
                String oldPattern = entry.getKey();
                String newPattern = entry.getValue();
                if (content.contains(oldPattern)) {
                    content = content.replace(oldPattern, newPattern);
                    System.out.println("Updated " + oldPattern + " → " + newPattern + " in " + file);
                }
            }
            
            // Also handle any remaining Matchers. references
            if (content.contains("Matchers.") && !content.equals(original)) {
                // Try generic replacement for any remaining Matchers.method()
                content = content.replace("Matchers.", "CoreMatchers.");
                System.out.println("Updated remaining Matchers. references in " + file);
            }
            
            if (!content.equals(original)) {
                Files.write(file, content.getBytes());
            }
            
        } catch (IOException e) {
            System.err.println("Error processing " + file + ": " + e.getMessage());
        }
    }
}