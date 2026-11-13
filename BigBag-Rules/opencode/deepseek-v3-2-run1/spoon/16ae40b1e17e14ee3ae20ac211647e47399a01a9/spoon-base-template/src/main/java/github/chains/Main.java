package github.chains;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;

public class Main {
    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("Usage: java -jar spoon-transformation.jar <source-directory>");
            System.err.println("Transforms net.lingala.zip4j.core.* imports/references to net.lingala.zip4j.*");
            System.exit(1);
        }
        
        Path sourceDir = Paths.get(args[0]);
        System.out.println("Applying zip4j package migration transformation to: " + sourceDir);
        
        List<Path> javaFiles = new ArrayList<>();
        Files.walkFileTree(sourceDir, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (file.toString().endsWith(".java")) {
                    javaFiles.add(file);
                }
                return FileVisitResult.CONTINUE;
            }
        });
        
        int totalChanges = 0;
        int filesModified = 0;
        
        for (Path javaFile : javaFiles) {
            try {
                String content = Files.readString(javaFile);
                String originalContent = content;
                
                // Replace imports
                content = content.replaceAll(
                    "import\\s+net\\.lingala\\.zip4j\\.core\\.([^*;]+);",
                    "import net.lingala.zip4j.$1;"
                );
                
                // Replace any other references (full qualified names in code)
                content = content.replaceAll(
                    "net\\.lingala\\.zip4j\\.core\\.(\\w+)",
                    "net.lingala.zip4j.$1"
                );
                
                // Count changes
                if (!content.equals(originalContent)) {
                    int changes = countChanges(originalContent, content);
                    Files.writeString(javaFile, content);
                    System.out.println("Modified: " + sourceDir.relativize(javaFile) + " (" + changes + " changes)");
                    totalChanges += changes;
                    filesModified++;
                }
            } catch (IOException e) {
                System.err.println("Error processing " + javaFile + ": " + e.getMessage());
            }
        }
        
        System.out.println("\nTransformation complete:");
        System.out.println("  Files modified: " + filesModified);
        System.out.println("  Total changes: " + totalChanges);
        System.out.println("\nBreaking change transformation:");
        System.out.println("  Old API pattern: net.lingala.zip4j.core.*");
        System.out.println("  New API pattern: net.lingala.zip4j.*");
        System.out.println("  Transformation: Package moved from 'core' subpackage to root package");
    }
    
    private static int countChanges(String original, String modified) {
        // Simple line-based change count
        String[] origLines = original.split("\n");
        String[] modLines = modified.split("\n");
        int changes = 0;
        
        for (int i = 0; i < Math.min(origLines.length, modLines.length); i++) {
            if (!origLines[i].equals(modLines[i])) {
                changes++;
            }
        }
        return changes + Math.abs(origLines.length - modLines.length);
    }
}
