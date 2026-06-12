package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.declaration.CtType;
import spoon.reflect.factory.Factory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Generic Spoon transformation to fix zip4j core package removal issue.
 * This transformation fixes the issue where net.lingala.zip4j.core.ZipFile
 * was removed in zip4j version 2.11.1 and is now directly in net.lingala.zip4j.ZipFile
 */
public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source_directory>");
            System.exit(1);
        }

        String sourceDirectory = args[0];
        
        try {
            // Read all Java files in the directory and replace the import
            Files.walk(Paths.get(sourceDirectory))
                .filter(path -> path.toString().endsWith(".java"))
                .forEach(Main::processJavaFile);
            
            System.out.println("Transformation completed successfully.");
        } catch (Exception e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private static void processJavaFile(java.nio.file.Path filePath) {
        try {
            String content = Files.readString(filePath);
            String newContent = content.replace(
                "net.lingala.zip4j.core.ZipFile",
                "net.lingala.zip4j.ZipFile"
            );
            
            if (!content.equals(newContent)) {
                Files.writeString(filePath, newContent);
                System.out.println("Updated: " + filePath);
            }
        } catch (IOException e) {
            System.err.println("Error processing file " + filePath + ": " + e.getMessage());
        }
    }
}