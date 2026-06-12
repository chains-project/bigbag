package github.chains;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

public class SimpleFix {
    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("Usage: java SimpleFix <source-directory>");
            System.exit(1);
        }
        
        Path sourceDir = Paths.get(args[0]);
        System.out.println("Scanning for parseEnchantment() calls in: " + sourceDir);
        
        // Find all Java files
        List<Path> javaFiles = Files.walk(sourceDir)
            .filter(p -> p.toString().endsWith(".java"))
            .collect(Collectors.toList());
        
        int totalReplacements = 0;
        int filesModified = 0;
        
        for (Path file : javaFiles) {
            try {
                String content = Files.readString(file);
                
                // Check if file contains parseEnchantment()
                if (content.contains("parseEnchantment()")) {
                    System.out.println("Processing: " + file);
                    
                    // Replace parseEnchantment() with getEnchant()
                    String newContent = content.replaceAll("\\.parseEnchantment\\(\\)", ".getEnchant()");
                    
                    // Count replacements
                    int oldCount = countOccurrences(content, "parseEnchantment()");
                    int newCount = countOccurrences(newContent, "parseEnchantment()");
                    int replacements = oldCount - newCount;
                    
                    if (replacements > 0) {
                        Files.writeString(file, newContent);
                        totalReplacements += replacements;
                        filesModified++;
                        System.out.println("  Replaced " + replacements + " occurrence(s)");
                    }
                }
            } catch (IOException e) {
                System.err.println("Error processing file " + file + ": " + e.getMessage());
            }
        }
        
        System.out.println("\nSummary:");
        System.out.println("  Files modified: " + filesModified);
        System.out.println("  Total replacements: " + totalReplacements);
        System.out.println("  Transformation completed!");
    }
    
    private static int countOccurrences(String str, String substr) {
        int count = 0;
        int idx = 0;
        while ((idx = str.indexOf(substr, idx)) != -1) {
            count++;
            idx += substr.length();
        }
        return count;
    }
}