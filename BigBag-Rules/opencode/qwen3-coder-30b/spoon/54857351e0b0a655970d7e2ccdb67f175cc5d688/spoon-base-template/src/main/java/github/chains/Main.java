package github.chains;

import java.io.*;
import java.nio.file.*;
import java.util.regex.*;

/**
 * Generic transformation to fix zip4j.core import issues in Maven projects.
 * This fixes breaking changes in zip4j 2.10.0 where net.lingala.zip4j.core 
 * package was removed and ZipFile moved to net.lingala.zip4j.
 */
public class Main {
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source_directory>");
            System.exit(1);
        }
        
        String sourceDirectory = args[0];
        Path sourcePath = Paths.get(sourceDirectory);
        
        // Create a backup directory
        Path backupPath = Paths.get(sourceDirectory + "_backup");
        try {
            Files.createDirectories(backupPath);
            // Copy all files to backup
            Files.walk(sourcePath)
                .filter(Files::isRegularFile)
                .forEach(file -> {
                    try {
                        Path target = backupPath.resolve(sourcePath.relativize(file));
                        Files.createDirectories(target.getParent());
                        Files.copy(file, target, StandardCopyOption.REPLACE_EXISTING);
                    } catch (IOException e) {
                        System.err.println("Error backing up file: " + e.getMessage());
                    }
                });
        } catch (IOException e) {
            System.err.println("Error creating backup: " + e.getMessage());
        }
        
        int fixedFiles = 0;
        
        try {
            // Find all Java files and fix imports
            fixedFiles = (int) Files.walk(sourcePath)
                .filter(path -> path.toString().endsWith(".java"))
                .filter(path -> {
                    try {
                        String content = new String(Files.readAllBytes(path));
                        return content.contains("net.lingala.zip4j.core");
                    } catch (IOException e) {
                        return false;
                    }
                })
                .mapToInt(path -> {
                    if (processJavaFile(path)) {
                        System.out.println("Fixed import in: " + path);
                        return 1;
                    }
                    return 0;
                })
                .sum();
            
            System.out.println("Transformation complete. Fixed " + fixedFiles + " files.");
            System.out.println("Backup created in: " + backupPath);
            
        } catch (IOException e) {
            System.err.println("Error processing files: " + e.getMessage());
            System.exit(1);
        }
    }
    
    private static boolean processJavaFile(Path filePath) {
        try {
            String content = new String(Files.readAllBytes(filePath));
            String updatedContent = content.replaceAll(
                "import\\s+net\\.lingala\\.zip4j\\.core\\.(.*?);",
                "import net.lingala.zip4j.$1;"
            );
            
            if (!content.equals(updatedContent)) {
                Files.write(filePath, updatedContent.getBytes());
                return true;
            }
        } catch (IOException e) {
            System.err.println("Error processing file " + filePath + ": " + e.getMessage());
            return false;
        }
        return false;
    }
}