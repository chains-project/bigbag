package github.chains;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Main {
    public static void main(String[] args) {
        // Process a directory of Java files
        if (args.length == 0) {
            System.err.println("Usage: java Main <source_directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        Path sourcePath = Paths.get(sourceDir);
        
        try (Stream<Path> paths = Files.walk(sourcePath)
                .filter(Files::isRegularFile)
                .filter(path -> path.toString().endsWith(".java"))) {
            
            List<Path> javaFiles = paths.collect(Collectors.toList());
            
            for (Path javaFile : javaFiles) {
                processJavaFile(javaFile);
            }
        } catch (IOException e) {
            System.err.println("Error processing files: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private static void processJavaFile(Path javaFile) {
        try {
            // Read the entire file content
            String content = new String(Files.readAllBytes(javaFile));
            boolean modified = false;
            
            // Replace imports
            String newContent = content.replace(
                "import org.mockito.runners.MockitoJUnitRunner;",
                "import org.mockito.junit.MockitoJUnitRunner;"
            );
            if (!newContent.equals(content)) {
                System.out.println("Updated import in: " + javaFile);
                modified = true;
            }
            
            // Replace @RunWith annotations
            newContent = newContent.replace(
                "@RunWith(MockitoJUnitRunner.class)",
                "@RunWith(org.mockito.junit.MockitoJUnitRunner.class)"
            );
            if (!newContent.equals(content)) {
                System.out.println("Updated @RunWith annotation in: " + javaFile);
                modified = true;
            }
            
            // If content was modified, write it back
            if (modified) {
                Files.write(javaFile, newContent.getBytes());
            }
            
        } catch (IOException e) {
            System.err.println("Error processing file " + javaFile + ": " + e.getMessage());
        }
    }
}