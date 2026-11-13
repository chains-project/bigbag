package github.chains;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Generic text-based transformation rule for migrating from javax.interceptor to jakarta.interceptor.
 * This handles the Jakarta EE 9+ migration where javax.* packages were renamed to jakarta.*
 * 
 * This transformation:
 * 1. Replaces import statements: import javax.interceptor.X -> import jakarta.interceptor.X
 * 2. Replaces fully-qualified type references in code: javax.interceptor.X -> jakarta.interceptor.X
 * 3. Works on any Java project by processing all .java files recursively
 * 
 * The transformation is generic and can be applied to any project by specifying
 * the source directory as the first argument.
 */
public class Main {
    
    /**
     * Main method that applies the transformation to a given source directory.
     * @param args args[0] should be the source directory path to transform
     */
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java github.chains.Main <source-directory>");
            System.err.println("Example: java github.chains.Main /path/to/project/src/main/java");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Applying javax.interceptor -> jakarta.interceptor transformation to: " + sourceDir);
        
        try {
            Path startDir = Paths.get(sourceDir);
            if (!Files.exists(startDir) || !Files.isDirectory(startDir)) {
                System.err.println("Error: Source directory does not exist or is not a directory: " + sourceDir);
                System.exit(1);
            }
            
            final int[] transformedFiles = {0};
            final int[] totalReplacements = {0};
            
            // Walk through all Java files
            Files.walkFileTree(startDir, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    if (file.toString().endsWith(".java")) {
                        List<String> lines = Files.readAllLines(file);
                        List<String> newLines = lines.stream()
                            .map(line -> {
                                String originalLine = line;
                                // Replace import statements
                                if (line.trim().startsWith("import javax.interceptor.")) {
                                    String newLine = line.replace("import javax.interceptor.", "import jakarta.interceptor.");
                                    System.out.println("Transforming import in " + file + ": " + originalLine.trim() + " -> " + newLine.trim());
                                    return newLine;
                                }
                                // Replace fully qualified references in code
                                // Note: This is a simple text replacement - in real code we'd want to be more precise
                                // but for javax->jakarta migration, simple replacement is usually safe
                                if (line.contains("javax.interceptor.")) {
                                    String newLine = line.replace("javax.interceptor.", "jakarta.interceptor.");
                                    System.out.println("Transforming reference in " + file + ": " + originalLine.trim() + " -> " + newLine.trim());
                                    return newLine;
                                }
                                return line;
                            })
                            .collect(Collectors.toList());
                        
                        // Check if any lines were modified
                        if (!newLines.equals(lines)) {
                            Files.write(file, newLines);
                            transformedFiles[0]++;
                            totalReplacements[0] += countDifferences(lines, newLines);
                        }
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
            
            System.out.println("\nTransformation complete!");
            System.out.println("Files transformed: " + transformedFiles[0]);
            System.out.println("Total replacements: " + totalReplacements[0]);
            
        } catch (Exception e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static int countDifferences(List<String> original, List<String> modified) {
        int count = 0;
        for (int i = 0; i < original.size(); i++) {
            if (!original.get(i).equals(modified.get(i))) {
                count++;
            }
        }
        return count;
    }
}