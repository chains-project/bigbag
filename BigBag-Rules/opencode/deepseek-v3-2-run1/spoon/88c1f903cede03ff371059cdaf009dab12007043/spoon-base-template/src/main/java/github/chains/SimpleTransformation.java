package github.chains;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;

public class SimpleTransformation {
    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("Usage: java SimpleTransformation <source-directory>");
            System.err.println("This transformation fixes zip4j 2.x breaking change:");
            System.err.println("  Old: net.lingala.zip4j.core.ZipFile");
            System.err.println("  New: net.lingala.zip4j.ZipFile");
            System.exit(1);
        }
        
        Path sourceDir = Paths.get(args[0]);
        System.out.println("Processing source directory: " + sourceDir);
        
        int fileCount = 0;
        int replacementCount = 0;
        
        // Walk through all Java files
        Files.walkFileTree(sourceDir, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (file.toString().endsWith(".java")) {
                    processJavaFile(file);
                }
                return FileVisitResult.CONTINUE;
            }
        });
        
        System.out.println("Transformation complete!");
    }
    
    private static void processJavaFile(Path file) throws IOException {
        List<String> lines = Files.readAllLines(file);
        List<String> newLines = new ArrayList<>();
        boolean modified = false;
        
        for (String line : lines) {
            String newLine = line;
            
            // Replace import statements
            if (line.contains("import net.lingala.zip4j.core.ZipFile")) {
                newLine = line.replace("import net.lingala.zip4j.core.ZipFile", "import net.lingala.zip4j.ZipFile");
                if (!newLine.equals(line)) {
                    System.out.println("Updated import in " + file + ": " + newLine);
                    modified = true;
                }
            }
            
            // Replace fully-qualified type references
            if (line.contains("net.lingala.zip4j.core.ZipFile")) {
                newLine = line.replace("net.lingala.zip4j.core.ZipFile", "net.lingala.zip4j.ZipFile");
                if (!newLine.equals(line)) {
                    System.out.println("Updated type reference in " + file + ": " + newLine);
                    modified = true;
                }
            }
            
            newLines.add(newLine);
        }
        
        if (modified) {
            Files.write(file, newLines);
        }
    }
}