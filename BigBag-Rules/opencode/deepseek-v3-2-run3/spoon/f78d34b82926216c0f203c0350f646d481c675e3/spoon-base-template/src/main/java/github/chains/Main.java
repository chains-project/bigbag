package github.chains;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Generic transformation rule for fixing breaking dependency updates
 * where only the package name has changed from develop.p2p.lib to tokyo.peya.lib.
 * 
 * This transformation:
 * 1. Updates import statements
 * 2. Updates fully-qualified type references in code
 * 3. Updates static import statements
 * 
 * The transformation is parameterized by old and new package names,
 * making it reusable for similar package rename migrations.
 */
public class Main {
    
    // Configuration: old and new package names
    private static final String OLD_PACKAGE = "develop.p2p.lib";
    private static final String NEW_PACKAGE = "tokyo.peya.lib";
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source-directory>");
            System.err.println("Example: java Main /path/to/project/src/main/java");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Applying transformation from " + OLD_PACKAGE + " to " + NEW_PACKAGE);
        System.out.println("Source directory: " + sourceDir);
        
        try {
            transformProject(sourceDir);
            System.out.println("Transformation completed successfully!");
        } catch (Exception e) {
            System.err.println("Transformation failed: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static void transformProject(String sourceDir) throws IOException {
        Path sourcePath = Paths.get(sourceDir);
        Path outputPath = Paths.get(sourceDir + "-transformed");
        
        // Copy directory structure
        Files.walk(sourcePath)
             .forEach(source -> {
                 try {
                     Path target = outputPath.resolve(sourcePath.relativize(source));
                     if (Files.isDirectory(source)) {
                         Files.createDirectories(target);
                     } else {
                         Files.copy(source, target);
                     }
                 } catch (IOException e) {
                     throw new RuntimeException(e);
                 }
             });
        
        // Process all Java files
        try (Stream<Path> paths = Files.walk(outputPath)) {
            paths.filter(Files::isRegularFile)
                 .filter(p -> p.toString().endsWith(".java"))
                 .forEach(Main::transformFile);
        }
    }
    
    private static void transformFile(Path file) {
        try {
            List<String> lines = Files.readAllLines(file);
            boolean changed = false;
            
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                if (line.contains(OLD_PACKAGE)) {
                    String newLine = line.replace(OLD_PACKAGE, NEW_PACKAGE);
                    if (!line.equals(newLine)) {
                        lines.set(i, newLine);
                        changed = true;
                        System.out.println("Transformed " + file + " line " + (i+1) + ":");
                        System.out.println("  " + line.trim());
                        System.out.println("  -> " + newLine.trim());
                    }
                }
            }
            
            if (changed) {
                Files.write(file, lines);
            }
        } catch (IOException e) {
            System.err.println("Error processing file " + file + ": " + e.getMessage());
        }
    }
}