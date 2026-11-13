package github.chains;

import java.io.*;
import java.nio.file.*;
import java.util.stream.Collectors;

public class SimpleTransformer {
    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("Usage: java SimpleTransformer <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Processing source directory: " + sourceDir);
        
        Files.walk(Paths.get(sourceDir))
            .filter(path -> path.toString().endsWith(".java"))
            .forEach(path -> {
                try {
                    String content = Files.readString(path);
                    String original = content;
                    
                    // Remove import for TrustedTagInspector
                    content = content.replace(
                        "import org.yaml.snakeyaml.inspector.TrustedTagInspector;\n",
                        ""
                    );
                    content = content.replace(
                        "import org.yaml.snakeyaml.inspector.TrustedTagInspector;",
                        ""
                    );
                    
                    // Replace constructor calls
                    content = content.replace(
                        "new TrustedTagInspector()",
                        "tag -> true"
                    );
                    
                    if (!content.equals(original)) {
                        System.out.println("Transformed: " + path);
                        Files.write(path, content.getBytes());
                    }
                } catch (IOException e) {
                    System.err.println("Error processing " + path + ": " + e.getMessage());
                }
            });
        
        System.out.println("Transformation completed");
    }
}