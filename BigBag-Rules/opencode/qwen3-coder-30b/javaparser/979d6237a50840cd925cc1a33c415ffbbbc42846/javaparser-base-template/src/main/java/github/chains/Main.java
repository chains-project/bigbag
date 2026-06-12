package github.chains;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

/**
 * Generic JavaParser transformation for fixing Struts2 API breaking changes.
 * This transformation replaces old Struts2 filter class references with new ones.
 */
public class Main {
    
    public static void main(String[] args) throws IOException {
        // Define the source directory to process
        String sourceDirectory = args.length > 0 ? args[0] : "/workspace/guice";
        
        // Process all Java files in the directory
        try (Stream<Path> paths = Files.walk(Paths.get(sourceDirectory))
                .filter(Files::isRegularFile)
                .filter(path -> path.toString().endsWith(".java"))) {
            
            paths.forEach(file -> {
                try {
                    processFile(file);
                } catch (Exception e) {
                    System.err.println("Error processing file " + file + ": " + e.getMessage());
                }
            });
        }
    }
    
    private static void processFile(Path filePath) throws IOException {
        // Read the file content
        StringBuilder content = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new FileReader(filePath.toFile()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append("\n");
            }
        }
        
        String fileContent = content.toString();
        String newContent = fileContent;
        
        // Replace the old class reference with the new one
        String oldClass = "org.apache.struts2.dispatcher.ng.filter.StrutsPrepareAndExecuteFilter";
        String newClass = "org.apache.struts2.dispatcher.filter.StrutsPrepareAndExecuteFilter";
        
        newContent = newContent.replace(oldClass, newClass);
        
        // If content changed, write it back
        if (!newContent.equals(fileContent)) {
            try (FileWriter writer = new FileWriter(filePath.toFile())) {
                writer.write(newContent);
            }
        }
    }
}
