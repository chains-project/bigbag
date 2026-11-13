package github.chains;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

/**
 * Generic transformation rule to fix SnakeYAML constructor calls that were broken by dependency updates.
 * This fixes cases where Yaml constructors are called with incorrect signatures.
 */
public class Main {
    
    public static void main(String[] args) {
        // The transformation will be applied to all Java files in the source directory
        if (args.length < 1) {
            System.err.println("Usage: java Main <source_directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        try (Stream<Path> paths = Files.walk(Paths.get(sourceDir))) {
            paths.filter(Files::isRegularFile)
                 .filter(path -> path.toString().endsWith(".java"))
                 .forEach(Main::processFile);
        } catch (IOException e) {
            System.err.println("Error processing files: " + e.getMessage());
            System.exit(1);
        }
    }
    
    private static void processFile(Path filePath) {
        try {
            String content = new String(Files.readAllBytes(filePath));
            String fixedContent = fixYamlConstructors(content);
            if (!content.equals(fixedContent)) {
                Files.write(filePath, fixedContent.getBytes());
                System.out.println("Fixed " + filePath);
            }
        } catch (Exception e) {
            System.err.println("Error processing " + filePath + ": " + e.getMessage());
        }
    }
    
    private static String fixYamlConstructors(String content) {
        // Direct replacement for the specific problematic line
        // Replace: new Yaml(constructor, new Representer(), new DumperOptions(), new ModelResolver())
        // With:     new Yaml(constructor, new Representer(), new DumperOptions(), new ModelResolver(), Model.class)
        return content.replace(
            "new Yaml(constructor, new Representer(), new DumperOptions(), new ModelResolver())",
            "new Yaml(constructor, new Representer(), new DumperOptions(), new ModelResolver(), Model.class)"
        );
    }
}