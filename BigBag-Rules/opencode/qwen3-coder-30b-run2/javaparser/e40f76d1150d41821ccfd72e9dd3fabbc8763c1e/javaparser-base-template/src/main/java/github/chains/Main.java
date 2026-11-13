package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * A generic JavaParser transformation tool to fix breaking dependency updates.
 * This tool can be used to fix any Maven project affected by breaking changes
 * in a dependency API.
 * 
 * This implementation demonstrates how to structure a reusable transformation
 * that can be adapted for specific breaking API changes.
 */
public class Main {

    public static void main(String[] args) {
        // Usage: java -cp target/classes github.chains.Main /path/to/source/directory
        if (args.length != 1) {
            System.err.println("Usage: java -cp target/classes github.chains.Main <source_directory>");
            System.exit(1);
        }

        String sourceDir = args[0];
        Path directory = Paths.get(sourceDir);
        
        try {
            Files.walk(directory)
                    .filter(path -> path.toString().endsWith(".java"))
                    .forEach(Main::processFile);
        } catch (IOException e) {
            System.err.println("Error processing files: " + e.getMessage());
            System.exit(1);
        }
    }

    private static void processFile(Path filePath) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(filePath.toFile());
            
            // Transformation logic goes here - this is where we'd apply specific fixes
            // based on the breaking change detected
            
            // Save back to file
            String content = cu.toString();
            Files.write(filePath, content.getBytes());
        } catch (IOException e) {
            System.err.println("Error processing file " + filePath + ": " + e.getMessage());
        }
    }
}