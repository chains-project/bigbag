package github.chains;

import java.io.File;
import java.util.regex.Pattern;
import java.util.List;
import java.util.ArrayList;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;

/**
 * Generic rule to identify and document plexus-utils 4.0.0 breaking changes.
 * 
 * Identifies usages of Xpp3Dom that will cause compilation failures
 * due to the migration from plexus-utils to plexus-xml in version 4.0.0.
 */
public class Main {
    public static void main(String[] args) {
        if (args.length == 0) {
            System.err.println("Usage: java Main <source_directory>");
            System.exit(1);
        }
        
        String sourceDirectory = args[0];
        int totalFiles = 0;
        int filesWithXpp3Dom = 0;
        
        try {
            // Check if the directory exists
            File dir = new File(sourceDirectory);
            if (!dir.exists() || !dir.isDirectory()) {
                System.err.println("Invalid directory: " + sourceDirectory);
                System.exit(1);
            }
            
            // Process files manually (avoiding lambda issues)
            File[] files = dir.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.getName().endsWith(".java")) {
                        totalFiles++;
                        try {
                            String content = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
                            if (content.contains("Xpp3Dom") || content.contains("org.codehaus.plexus.util.xml.Xpp3Dom")) {
                                filesWithXpp3Dom++;
                                System.out.println("Found Xpp3Dom usage in: " + file.getAbsolutePath());
                            }
                        } catch (Exception e) {
                            // Ignore errors reading files
                        }
                    }
                }
            }
                
            System.out.println("Scanned " + totalFiles + " Java files.");
            System.out.println("Files containing Xpp3Dom references: " + filesWithXpp3Dom);
            
            if (filesWithXpp3Dom > 0) {
                System.out.println("\nFix required: Add plexus-xml dependency to pom.xml:");
                System.out.println("<dependency>");
                System.out.println("  <groupId>org.codehaus.plexus</groupId>");
                System.out.println("  <artifactId>plexus-xml</artifactId>");
                System.out.println("  <version>3.0.0</version>");
                System.out.println("</dependency>");
                System.out.println("\nThis resolves the compilation error caused by Xpp3Dom class");
                System.out.println("migration from plexus-utils to plexus-xml in version 4.0.0.");
            } else {
                System.out.println("No Xpp3Dom references found.");
            }
            
        } catch (Exception e) {
            System.err.println("Error scanning files: " + e.getMessage());
            System.exit(1);
        }
    }
}