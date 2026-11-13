package github.chains;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

/**
 * Generic transformation rule for BouncyCastle TLS API migration.
 * 
 * Breaking change pattern:
 * - Old API: org.bouncycastle.crypto.tls.TlsFatalAlert and AlertDescription
 * - New API: These classes have been removed in BouncyCastle 1.67+
 * - Transformation: Replace TlsFatalAlert with IOException
 * 
 * This transformation can be applied to any Java project affected by this
 * breaking change by simply changing the input source directory.
 */
public class Main {
    
    // Configuration - these can be parameterized for different breaking changes
    private static final String OLD_PACKAGE_PATTERN = "org\\.bouncycastle\\.crypto\\.tls\\..*";
    private static final String OLD_EXCEPTION_PATTERN = "TlsFatalAlert";
    private static final String OLD_DESCRIPTION_PATTERN = "AlertDescription\\.bad_record_mac";
    private static final String NEW_EXCEPTION = "java.io.IOException";
    private static final String NEW_MESSAGE = "\"Bad record MAC\"";
    
    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("Usage: java -jar transformation.jar <source-directory>");
            System.err.println("\nApplies BouncyCastle TLS API migration:");
            System.err.println("  - Removes imports from " + OLD_PACKAGE_PATTERN);
            System.err.println("  - Replaces " + OLD_EXCEPTION_PATTERN + "(" + OLD_DESCRIPTION_PATTERN + ")");
            System.err.println("    with " + NEW_EXCEPTION + "(" + NEW_MESSAGE + ")");
            System.exit(1);
        }
        
        Path sourceDir = Paths.get(args[0]);
        System.out.println("Applying BouncyCastle TLS API migration to: " + sourceDir);
        
        List<Path> javaFiles = findJavaFiles(sourceDir);
        System.out.println("Found " + javaFiles.size() + " Java files to process");
        
        int totalChanges = 0;
        int modifiedFiles = 0;
        
        for (Path file : javaFiles) {
            FileTransformResult result = transformFile(file);
            if (result.modified) {
                modifiedFiles++;
                totalChanges += result.changeCount;
                System.out.println("  Modified: " + sourceDir.relativize(file) + 
                                 " (" + result.changeCount + " changes)");
            }
        }
        
        System.out.println("\nTransformation completed:");
        System.out.println("  Modified files: " + modifiedFiles + " / " + javaFiles.size());
        System.out.println("  Total changes: " + totalChanges);
        System.out.println("\nChanges applied:");
        System.out.println("  1. Removed imports from " + OLD_PACKAGE_PATTERN);
        System.out.println("  2. Replaced " + OLD_EXCEPTION_PATTERN + "(" + OLD_DESCRIPTION_PATTERN + ")");
        System.out.println("     with " + NEW_EXCEPTION + "(" + NEW_MESSAGE + ")");
        System.out.println("  3. Replaced other " + OLD_EXCEPTION_PATTERN + " constructors");
        System.out.println("     with " + NEW_EXCEPTION + "(\"TLS error\")");
    }
    
    private static List<Path> findJavaFiles(Path dir) throws IOException {
        List<Path> files = new ArrayList<>();
        try (var stream = Files.walk(dir)) {
            stream.filter(path -> path.toString().endsWith(".java"))
                  .forEach(files::add);
        }
        return files;
    }
    
    private static FileTransformResult transformFile(Path file) throws IOException {
        List<String> lines = Files.readAllLines(file);
        List<String> newLines = new ArrayList<>();
        boolean modified = false;
        int changeCount = 0;
        
        for (String line : lines) {
            String originalLine = line;
            
            // Remove imports from the old package
            if (line.matches("\\s*import\\s+" + OLD_PACKAGE_PATTERN + "\\s*;.*")) {
                modified = true;
                changeCount++;
                continue; // Skip this import line
            }
            
            // Replace TlsFatalAlert(AlertDescription.bad_record_mac)
            line = line.replaceAll(
                "new\\s+" + OLD_EXCEPTION_PATTERN + "\\s*\\(\\s*" + OLD_DESCRIPTION_PATTERN + "\\s*\\)",
                "new " + NEW_EXCEPTION + "(" + NEW_MESSAGE + ")");
            
            // Replace any other TlsFatalAlert constructor call
            line = line.replaceAll(
                "new\\s+" + OLD_EXCEPTION_PATTERN + "\\s*\\([^)]*\\)",
                "new " + NEW_EXCEPTION + "(\"TLS error\")");
            
            if (!line.equals(originalLine)) {
                modified = true;
                changeCount++;
            }
            
            newLines.add(line);
        }
        
        if (modified) {
            Files.write(file, newLines);
        }
        
        return new FileTransformResult(modified, changeCount);
    }
    
    private static class FileTransformResult {
        final boolean modified;
        final int changeCount;
        
        FileTransformResult(boolean modified, int changeCount) {
            this.modified = modified;
            this.changeCount = changeCount;
        }
    }
}