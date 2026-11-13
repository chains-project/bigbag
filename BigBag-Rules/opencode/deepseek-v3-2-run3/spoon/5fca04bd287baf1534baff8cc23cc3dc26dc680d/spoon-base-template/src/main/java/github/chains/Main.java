package github.chains;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Generic Spoon transformation rule for cactoos dependency removal.
 * 
 * This transformation handles the breaking change where org.cactoos.* classes
 * were removed from a dependency. It transforms:
 * 1. new HexOf(new BytesOf(...)).asString() -> bytesToHex(...)
 * 2. new ListOf(...) -> java.util.Arrays.asList(...)
 * 3. Removes all org.cactoos imports
 * 4. Adds bytesToHex utility method to classes that need it
 * 
 * This is a generic, reusable transformation that can be applied to any
 * project affected by the same breaking change by simply changing the
 * input source directory path.
 */
public class Main {
    public static void main(String[] args) throws IOException {
        if (args.length < 2) {
            System.err.println("Usage: java github.chains.Main <source-directory> <output-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        String outputDir = args[1];
        
        System.out.println("Transforming source directory: " + sourceDir);
        System.out.println("Output directory: " + outputDir);
        
        // Create output directory
        Files.createDirectories(Paths.get(outputDir));
        
        // Process all Java files
        processDirectory(Paths.get(sourceDir), Paths.get(outputDir));
        
        System.out.println("Transformation completed!");
    }
    
    private static void processDirectory(Path sourceDir, Path outputDir) throws IOException {
        Files.walk(sourceDir)
            .filter(path -> path.toString().endsWith(".java"))
            .forEach(sourceFile -> {
                try {
                    processFile(sourceFile, outputDir.resolve(sourceDir.relativize(sourceFile)));
                } catch (IOException e) {
                    System.err.println("Error processing file " + sourceFile + ": " + e.getMessage());
                }
            });
    }
    
    private static void processFile(Path sourceFile, Path outputFile) throws IOException {
        String content = Files.readString(sourceFile);
        String transformed = transformContent(content);
        
        // Create parent directories if needed
        Files.createDirectories(outputFile.getParent());
        Files.writeString(outputFile, transformed);
        
        System.out.println("Processed: " + sourceFile);
    }
    
    private static String transformContent(String content) {
        // Remove cactoos imports
        String transformed = content.replaceAll("import\\s+org\\.cactoos\\.io\\.BytesOf;\\s*", "");
        transformed = transformed.replaceAll("import\\s+org\\.cactoos\\.text\\.HexOf;\\s*", "");
        transformed = transformed.replaceAll("import\\s+org\\.cactoos\\.list\\.ListOf;\\s*", "");
        transformed = transformed.replaceAll("import\\s+org\\.cactoos\\.*;\\s*", "");
        
        // Pattern to match: new HexOf(new BytesOf(...)).asString()
        Pattern pattern = Pattern.compile("new\\s+HexOf\\s*\\(\\s*new\\s+BytesOf\\s*\\(([\\s\\S]*?)\\)\\s*\\)\\s*\\.\\s*asString\\s*\\(\\)", 
                                          Pattern.DOTALL);
        
        Matcher matcher = pattern.matcher(transformed);
        StringBuffer result = new StringBuffer();
        
        while (matcher.find()) {
            String byteArrayExpr = matcher.group(1).trim();
            String replacement = "bytesToHex(" + byteArrayExpr + ")";
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);
        
        transformed = result.toString();
        
        // Replace new ListOf(...) with Arrays.asList(...)
        transformed = transformed.replaceAll("new\\s+ListOf\\s*<([^>]+)>\\s*\\(", "java.util.Arrays.asList(");
        transformed = transformed.replaceAll("new\\s+ListOf\\s*\\(\\s*", "java.util.Arrays.asList(");
        
        // Check if we need to add utility method
        if (transformed.contains("bytesToHex(")) {
            // Add utility method at the end of the class
            transformed = addHexUtilityMethod(transformed);
        }
        
        return transformed;
    }
    
    private static String addHexUtilityMethod(String content) {
        // Find the last closing brace of the class
        int lastBrace = content.lastIndexOf('}');
        if (lastBrace == -1) {
            return content;
        }
        
        // Check if bytesToHex already exists (avoid duplicates)
        if (content.contains("private static String bytesToHex(byte[] bytes)")) {
            return content;
        }
        
        // Insert utility method before the last closing brace
        String utilityMethod = "\n\n    private static String bytesToHex(byte[] bytes) {\n" +
                               "        StringBuilder hex = new StringBuilder(bytes.length * 2);\n" +
                               "        for (byte b : bytes) {\n" +
                               "            hex.append(String.format(\"%02x\", b));\n" +
                               "        }\n" +
                               "        return hex.toString();\n" +
                               "    }";
        
        return content.substring(0, lastBrace) + utilityMethod + content.substring(lastBrace);
    }
}