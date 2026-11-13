package github.chains;

import java.io.*;
import java.nio.file.*;
import java.util.regex.*;
import java.util.stream.*;

public class Main {
    public static void main(String[] args) throws IOException {
        // Process all Java files in the current directory and subdirectories
        Path projectRoot = Paths.get("/workspace/junit-quickcheck");
        
        try (Stream<Path> paths = Files.walk(projectRoot)
                .filter(Files::isRegularFile)
                .filter(path -> path.toString().endsWith(".java"))) {
            
            paths.forEach(Main::transformFile);
        }
    }
    
    private static void transformFile(Path filePath) {
        try {
            String content = Files.readString(filePath);
            String modifiedContent = content.replaceAll(
                "import org.mockito.runners.MockitoJUnitRunner;",
                "import org.mockito.junit.MockitoJUnitRunner;"
            );
            
            if (!content.equals(modifiedContent)) {
                Files.writeString(filePath, modifiedContent);
                System.out.println("Updated: " + filePath);
            }
        } catch (Exception e) {
            System.err.println("Error processing file: " + filePath + " - " + e.getMessage());
        }
    }
}