package github.chains;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

public class TextReplacer {
    
    // Pattern to match XEnchantment.matchXEnchantment("power").get().parseEnchantment()
    private static final Pattern PARSE_ENCHANTMENT_PATTERN = 
        Pattern.compile("(XEnchantment\\.matchXEnchantment\\([^)]+\\)\\.get\\(\\)\\.)parseEnchantment\\(\\)");
    
    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("Usage: java -jar text-replacer.jar <source-directory>");
            System.err.println("Example: java -jar text-replacer.jar /path/to/project/src");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Processing source directory: " + sourceDir);
        
        List<Path> javaFiles = findJavaFiles(sourceDir);
        System.out.println("Found " + javaFiles.size() + " Java files");
        
        int totalReplacements = 0;
        
        for (Path javaFile : javaFiles) {
            int replacements = processFile(javaFile);
            totalReplacements += replacements;
            
            if (replacements > 0) {
                System.out.println("  " + javaFile + ": " + replacements + " replacement(s)");
            }
        }
        
        System.out.println("\nTotal replacements: " + totalReplacements);
        System.out.println("Transformation completed successfully");
    }
    
    private static List<Path> findJavaFiles(String sourceDir) throws IOException {
        List<Path> javaFiles = new ArrayList<>();
        Files.walk(Paths.get(sourceDir))
            .filter(path -> path.toString().endsWith(".java"))
            .forEach(javaFiles::add);
        return javaFiles;
    }
    
    private static int processFile(Path javaFile) throws IOException {
        String content = Files.readString(javaFile);
        Matcher matcher = PARSE_ENCHANTMENT_PATTERN.matcher(content);
        
        int replacementCount = 0;
        StringBuffer result = new StringBuffer();
        
        while (matcher.find()) {
            // Replace parseEnchantment() with getEnchant()
            String replacement = matcher.group(1) + "getEnchant()";
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
            replacementCount++;
        }
        matcher.appendTail(result);
        
        if (replacementCount > 0) {
            Files.writeString(javaFile, result.toString());
        }
        
        return replacementCount;
    }
}