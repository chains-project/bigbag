package github.chains;

import java.io.*;
import java.nio.file.*;
import java.util.regex.*;

public class Main {
    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source_directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Processing source directory: " + sourceDir);
        
        // Pattern to find addEnabledLanguages calls with Set<Language> argument
        Pattern pattern = Pattern.compile(
            "addEnabledLanguages\\s*\\(\\s*([^)]+?\\s*\\.\\s*getEnabledLanguages\\s*\\(\\s*\\))\\s*\\)",
            Pattern.DOTALL
        );
        
        // Replacement pattern
        String replacement = "addEnabledLanguages($1.toArray(new org.sonarsource.sonarlint.core.commons.Language[0]))";
        
        final int[] totalReplacements = new int[1];
        
        // Walk through all Java files
        Files.walk(Paths.get(sourceDir))
            .filter(path -> path.toString().endsWith(".java"))
            .forEach(path -> {
                try {
                    String content = Files.readString(path);
                    Matcher matcher = pattern.matcher(content);
                    
                    if (matcher.find()) {
                        String newContent = matcher.replaceAll(replacement);
                        if (!content.equals(newContent)) {
                            Files.write(path, newContent.getBytes());
                            System.out.println("Transformed: " + path);
                            totalReplacements[0]++;
                        }
                    }
                } catch (IOException e) {
                    System.err.println("Error processing file: " + path);
                    e.printStackTrace();
                }
            });
        
        System.out.println("Total replacements: " + totalReplacements[0]);
    }
}