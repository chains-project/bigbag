package github.chains;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class SimpleMain {
    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("Usage: java SimpleMain <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Processing source directory: " + sourceDir);
        
        try (Stream<Path> paths = Files.walk(Paths.get(sourceDir))) {
            List<Path> javaFiles = paths
                .filter(Files::isRegularFile)
                .filter(p -> p.toString().endsWith(".java"))
                .collect(Collectors.toList());
            
            int transformedFiles = 0;
            
            for (Path javaFile : javaFiles) {
                String content = Files.readString(javaFile);
                String originalContent = content;
                
                // Replace javax.validation with jakarta.validation
                content = content.replace("javax.validation", "jakarta.validation");
                
                if (!content.equals(originalContent)) {
                    Files.writeString(javaFile, content);
                    transformedFiles++;
                    System.out.println("Transformed: " + javaFile);
                }
            }
            
            System.out.println("Total files transformed: " + transformedFiles);
        }
        
        System.out.println("Transformation complete.");
    }
}