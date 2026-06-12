package github.chains;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Generic transformation to fix SLF4J version incompatibility issues
 * when upgrading logback-classic from older versions to 1.4.4+
 * 
 * This transformation updates SLF4J dependencies in pom.xml files to ensure
 * compatibility with logback-classic 1.4.4 which requires SLF4J 2.0.0+
 * 
 * Usage: java -cp target/classes github.chains.Main /path/to/project
 */
public class Main {
    
    public static void main(String[] args) {
        if (args.length == 0) {
            System.err.println("Usage: java -cp ... github.chains.Main <path-to-project>");
            System.exit(1);
        }
        
        String projectPath = args[0];
        Path pomPath = Paths.get(projectPath, "pom.xml");
        
        if (!Files.exists(pomPath)) {
            System.err.println("pom.xml not found at: " + pomPath);
            System.exit(1);
        }
        
        try {
            // Read the pom.xml file
            List<String> lines = Files.readAllLines(pomPath);
            
            // Apply the transformation by updating SLF4J dependency versions
            boolean modified = updateSLF4JDependencies(lines);
            
            if (modified) {
                // Write back the modified content
                Files.write(pomPath, lines);
                System.out.println("SLF4J version compatibility fix applied to: " + pomPath);
                System.out.println("Updated SLF4J dependencies to version 2.0.9");
            } else {
                System.out.println("No changes needed - SLF4J dependencies already compatible");
            }
            
        } catch (IOException e) {
            System.err.println("Error processing pom.xml: " + e.getMessage());
            System.exit(1);
        }
    }
    
    /**
     * Updates SLF4J dependencies to version 2.0.9 for compatibility with logback-classic 1.4.4+
     */
    private static boolean updateSLF4JDependencies(List<String> lines) {
        boolean modified = false;
        
        // Simple approach: just check if we find version 1.7.x and replace with 2.0.9
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            
            // Check for any slf4j version that is 1.7.x or older
            if (line.contains("slf4j") && line.contains("<version>") && 
                (line.contains("1.7.") || line.contains("1.6.") || line.contains("1.5."))) {
                // Check if it's a version we want to update
                if (line.contains("<version>1.7") || line.contains("<version>1.6") || line.contains("<version>1.5")) {
                    // Replace with 2.0.9
                    if (line.contains("slf4j-api")) {
                        lines.set(i, line.replaceAll("(?<=<version>)([^<]+)(?=</version>)", "2.0.9"));
                        System.out.println("Updated slf4j-api version");
                        modified = true;
                    } else if (line.contains("jul-to-slf4j")) {
                        lines.set(i, line.replaceAll("(?<=<version>)([^<]+)(?=</version>)", "2.0.9"));
                        System.out.println("Updated jul-to-slf4j version");
                        modified = true;
                    } else if (line.contains("log4j-over-slf4j")) {
                        lines.set(i, line.replaceAll("(?<=<version>)([^<]+)(?=</version>)", "2.0.9"));
                        System.out.println("Updated log4j-over-slf4j version");
                        modified = true;
                    } else if (line.contains("jcl-over-slf4j")) {
                        lines.set(i, line.replaceAll("(?<=<version>)([^<]+)(?=</version>)", "2.0.9"));
                        System.out.println("Updated jcl-over-slf4j version");
                        modified = true;
                    }
                }
            }
        }
        
        return modified;
    }
}