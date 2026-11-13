package github.chains;

import java.io.*;
import java.nio.file.*;
import java.util.regex.*;

/**
 * Simple textual replacement utility to fix Hamcrest breaking change
 * This serves as a working alternative for the Spoon-based transformation
 */
public class HamcrestFix {
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java HamcrestFix <source-directory>");
            System.exit(1);
        }

        String sourceDir = args[0];
        Path dir = Paths.get(sourceDir);
        
        try {
            // Process all Java files
            Files.walk(dir)
                .filter(path -> path.toString().endsWith(".java"))
                .forEach(HamcrestFix::processFile);
                
            System.out.println("Hamcrest fix completed successfully.");
        } catch (IOException e) {
            System.err.println("Error processing files: " + e.getMessage());
            System.exit(1);
        }
    }
    
    private static void processFile(Path file) {
        try {
            String content = new String(Files.readAllBytes(file));
            String modified = content;
            
            // Fix StringContains calls with 2 arguments
            modified = modified.replaceAll(
                "new StringContains\\(true,\\s*(.*?)\\)", 
                "new StringContains($1)"
            );
            
            modified = modified.replaceAll(
                "new StringContains\\(false,\\s*(.*?)\\)", 
                "new StringContains($1)"
            );
            
            // Fix StringStartsWith calls with 2 arguments
            modified = modified.replaceAll(
                "new StringStartsWith\\(true,\\s*(.*?)\\)", 
                "new StringStartsWith($1)"
            );
            
            modified = modified.replaceAll(
                "new StringStartsWith\\(false,\\s*(.*?)\\)", 
                "new StringStartsWith($1)"
            );
            
            // Write back if modified
            if (!modified.equals(content)) {
                Files.write(file, modified.getBytes());
                System.out.println("Fixed: " + file);
            }
        } catch (IOException e) {
            System.err.println("Error processing " + file + ": " + e.getMessage());
        }
    }
}