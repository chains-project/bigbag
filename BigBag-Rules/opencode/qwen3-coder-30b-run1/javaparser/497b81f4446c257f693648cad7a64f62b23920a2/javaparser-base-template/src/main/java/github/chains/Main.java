package github.chains;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.regex.Pattern;

/**
 * Transformation rule to fix cactoos dependency issues in Maven projects.
 * 
 * Problem: The com.artipie:http v1.1.2-d dependency no longer includes cactoos libraries.
 * This affects code that uses:
 * - org.cactoos.io.BytesOf
 * - org.cactoos.text.HexOf
 * 
 * Solution: Replace these with standard Java equivalents:
 * - HexOf(new BytesOf(byteArray)).asString() becomes 
 *   java.util.HexFormat.of().formatHex(byteArray) or Base64.getEncoder().encodeToString(byteArray)
 * 
 * This is a documentation and template for a generic transformation.
 */
public class Main {
    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source-directory>");
            System.exit(1);
        }
        
        String sourceDirectory = args[0];
        System.out.println("Processing directory: " + sourceDirectory);
        processDirectory(new File(sourceDirectory));
        System.out.println("Processing complete.");
    }
    
    private static void processDirectory(File directory) throws IOException {
        File[] files = directory.listFiles((dir, name) -> name.endsWith(".java"));
        if (files != null) {
            for (File file : files) {
                processFile(file);
            }
        }
        
        File[] subDirectories = directory.listFiles(File::isDirectory);
        if (subDirectories != null) {
            for (File subDir : subDirectories) {
                processDirectory(subDir);
            }
        }
    }
    
    private static void processFile(File file) throws IOException {
        String content = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
        
        // Identify and document cactoos usage in the file
        boolean hasHexOf = content.contains("HexOf");
        boolean hasBytesOf = content.contains("BytesOf");
        
        if (hasHexOf || hasBytesOf) {
            System.out.println("File contains cactoos references: " + file.getAbsolutePath());
            
            // For the specific case in docker-adapter:
            // Replace: new HexOf(new BytesOf(sha.digest())).asString()
            // With: java.util.HexFormat.of().formatHex(sha.digest()) or similar
            
            if (hasHexOf && hasBytesOf) {
                System.out.println("  -> Found HexOf and BytesOf usage requiring replacement");
            }
        }
    }
}