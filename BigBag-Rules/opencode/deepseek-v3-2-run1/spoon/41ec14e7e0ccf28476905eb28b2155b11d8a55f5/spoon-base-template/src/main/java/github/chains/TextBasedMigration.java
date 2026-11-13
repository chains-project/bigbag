package github.chains;

import java.io.*;
import java.nio.file.*;
import java.util.*;

public class TextBasedMigration {
    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("Usage: java -jar migration.jar <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Applying Jakarta EE migration transformation to: " + sourceDir);
        
        // Map of old to new package patterns
        Map<String, String> packageMappings = new HashMap<>();
        packageMappings.put("javax.validation", "jakarta.validation");
        packageMappings.put("javax.annotation", "jakarta.annotation");
        packageMappings.put("javax.servlet", "jakarta.servlet");
        packageMappings.put("javax.persistence", "jakarta.persistence");
        packageMappings.put("javax.transaction", "jakarta.transaction");
        packageMappings.put("javax.ws.rs", "jakarta.ws.rs");
        packageMappings.put("javax.xml.bind", "jakarta.xml.bind");
        
        int fileCount = 0;
        int replacementCount = 0;
        
        Files.walk(Paths.get(sourceDir))
            .filter(Files::isRegularFile)
            .filter(p -> p.toString().endsWith(".java"))
            .forEach(file -> {
                try {
                    boolean modified = false;
                    String content = new String(Files.readAllBytes(file));
                    String originalContent = content;
                    
                    // Apply all package mappings
                    for (Map.Entry<String, String> mapping : packageMappings.entrySet()) {
                        String oldPkg = mapping.getKey();
                        String newPkg = mapping.getValue();
                        
                        // Replace imports (more specific pattern)
                        String importPattern = "import\\s+" + oldPkg.replace(".", "\\.") + "\\.[^;]+;";
                        String replacement = content.replaceAll(importPattern, match -> {
                            System.out.println("Changing import in " + file + ": " + match);
                            return match.replace(oldPkg, newPkg);
                        });
                        
                        if (!replacement.equals(content)) {
                            content = replacement;
                            modified = true;
                        }
                        
                        // Replace fully qualified type references in code
                        String typePattern = "\\b" + oldPkg.replace(".", "\\.") + "\\.[A-Za-z0-9._]+\\b";
                        replacement = content.replaceAll(typePattern, match -> {
                            if (!match.contains("." + oldPkg + ".")) { // Avoid double replacement
                                System.out.println("Changing type reference in " + file + ": " + match);
                                return match.replace(oldPkg, newPkg);
                            }
                            return match;
                        });
                        
                        if (!replacement.equals(content)) {
                            content = replacement;
                            modified = true;
                        }
                    }
                    
                    if (modified && !content.equals(originalContent)) {
                        Files.write(file, content.getBytes());
                        System.out.println("Updated: " + file);
                    }
                } catch (IOException e) {
                    System.err.println("Error processing " + file + ": " + e.getMessage());
                }
            });
        
        System.out.println("\nTransformation complete.");
        System.out.println("Package mappings applied:");
        for (Map.Entry<String, String> mapping : packageMappings.entrySet()) {
            System.out.println("  " + mapping.getKey() + " -> " + mapping.getValue());
        }
    }
}