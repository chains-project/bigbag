package github.chains;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Pattern;

/**
 * Generic transformation rule to document the fix for breaking changes in plexus-utils 4.0.0
 * Specifically addresses removal of org.codehaus.plexus.util.xml.Xpp3Dom
 * 
 * This tool documents the problem and solution rather than automating the complete fix.
 * The Xpp3Dom class was removed from plexus-utils 4.0.0 and requires:
 * 
 * 1. Adding plexus-xml dependency to restore functionality (short-term)
 * 2. Refactoring to use standard XML APIs (long-term)
 */
public class Main {
    
    public static void main(String[] args) throws IOException {
        // Process all Java files in the source directory
        String sourceDir = args.length > 0 ? args[0] : "/workspace/pgpverify-maven-plugin/src";
        
        System.out.println("Analyzing Xpp3Dom usage in " + sourceDir);
        System.out.println("Problem: Xpp3Dom class was removed from plexus-utils 4.0.0");
        System.out.println("Solution options:");
        System.out.println("1. Add dependency on org.codehaus.plexus:plexus-xml");
        System.out.println("2. Refactor to use standard Java XML APIs (DOM/StAX)");
        System.out.println();
        
        // Show what needs to be fixed
        Files.walk(Paths.get(sourceDir))
            .filter(path -> path.toString().endsWith(".java"))
            .forEach(Main::analyzeFile);
            
        System.out.println("\n=== SOLUTION ===");
        System.out.println("For the pgpverify-maven-plugin, add this to pom.xml:");
        System.out.println("<dependency>");
        System.out.println("    <groupId>org.codehaus.plexus</groupId>");
        System.out.println("    <artifactId>plexus-xml</artifactId>");
        System.out.println("    <version>1.1.1</version>");
        System.out.println("</dependency>");
    }
    
    private static void analyzeFile(Path filePath) {
        try {
            String content = new String(Files.readAllBytes(filePath));
            
            if (content.contains("Xpp3Dom")) {
                System.out.println("FILE: " + filePath);
                
                // Count Xpp3Dom occurrences
                long count = content.lines()
                    .filter(line -> line.contains("Xpp3Dom"))
                    .count();
                    
                System.out.println("  Contains " + count + " Xpp3Dom references");
                
                // Show lines with Xpp3Dom
                content.lines()
                    .filter(line -> line.contains("Xpp3Dom"))
                    .forEach(line -> System.out.println("    " + line.trim()));
                System.out.println();
            }
        } catch (Exception e) {
            System.err.println("Error processing " + filePath + ": " + e.getMessage());
        }
    }
}
