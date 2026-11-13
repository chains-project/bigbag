package github.chains;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Generic transformation for fixing HTTP dependency breaking changes.
 * Specifically addresses Connection.accept calls that need Headers conversion.
 */
public class Main {

    public static void main(String[] args) throws IOException {
        // Process all Java files in the project
        String sourceDir = args.length > 0 ? args[0] : "/workspace/docker-adapter/src/main/java";
        
        // Find all Java files recursively
        List<Path> javaFiles = Files.walk(Paths.get(sourceDir))
            .filter(path -> path.toString().endsWith(".java"))
            .collect(Collectors.toList());
            
        // Transform each file
        for (Path file : javaFiles) {
            transformFile(file);
        }
    }
    
    private static void transformFile(Path file) throws IOException {
        String content = new String(Files.readAllBytes(file));
        
        // Simple pattern replacement for the specific issue
        // Replace: connection.accept(status, headers, body)
        // With: connection.accept(status, new Headers.From(headers), body)
        
        // Pattern to match the specific API call we identified
        String pattern = "(connection\\.accept\\([^)]*?,\\s*)([^,)]+)(,\\s*[^)]+\\))";
        
        // Replacement that wraps the headers parameter in Headers.From()
        String replacement = "$1new com.artipie.http.Headers.From($2)$3";
        
        String updatedContent = content.replaceAll(pattern, replacement);
        
        if (!content.equals(updatedContent)) {
            Files.write(file, updatedContent.getBytes());
            System.out.println("Updated: " + file);
        }
    }
}