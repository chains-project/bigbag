package github.chains;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;
import java.util.stream.Stream;

/**
 * Generic transformation rule for migrating classes that move between packages.
 * 
 * BREAKING CHANGE PATTERN: Class relocation
 * Old API: eu.europa.esig.dss.pades.CertificationPermission
 * New API: eu.europa.esig.dss.enumerations.CertificationPermission
 * 
 * TRANSFORMATION RULE:
 * 1. Update import statements
 * 2. Update fully-qualified references in code
 * 
 * This transformation is generic and reusable for any project affected by
 * this specific breaking change. The transformation can be parameterized
 * for other class relocations by changing OLD_FQCN and NEW_FQCN constants.
 * 
 * USAGE:
 *   java -jar transform.jar <source-directory>
 *   
 * EXAMPLE:
 *   java -jar transform.jar /path/to/project/src/main/java
 *   
 * The transformation will process all .java files in the directory
 * and its subdirectories.
 */
public class Main {
    
    // CONFIGURATION: These constants define the transformation
    // For true genericity, these could be command-line parameters
    private static final String OLD_FQCN = "eu.europa.esig.dss.pades.CertificationPermission";
    private static final String NEW_FQCN = "eu.europa.esig.dss.enumerations.CertificationPermission";
    
    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            printUsage();
            System.exit(1);
        }
        
        Path sourceDir = Paths.get(args[0]);
        if (!Files.exists(sourceDir) || !Files.isDirectory(sourceDir)) {
            System.err.println("Error: Source directory does not exist: " + sourceDir);
            System.exit(1);
        }
        
        System.out.println("=========================================");
        System.out.println("Generic Package Migration Transformation");
        System.out.println("=========================================");
        System.out.println("Source: " + sourceDir);
        System.out.println("Transformation: " + OLD_FQCN + " -> " + NEW_FQCN);
        System.out.println("=========================================");
        
        int processedFiles = 0;
        int modifiedFiles = 0;
        
        try (Stream<Path> stream = Files.walk(sourceDir)) {
            Iterator<Path> iterator = stream.iterator();
            while (iterator.hasNext()) {
                Path filePath = iterator.next();
                if (filePath.toString().endsWith(".java")) {
                    processedFiles++;
                    if (processFile(filePath)) {
                        modifiedFiles++;
                    }
                }
            }
        }
        
        System.out.println("=========================================");
        System.out.println("SUMMARY");
        System.out.println("=========================================");
        System.out.println("Processed files: " + processedFiles);
        System.out.println("Modified files:  " + modifiedFiles);
        System.out.println("Transformation completed successfully!");
        System.out.println("=========================================");
    }
    
    private static void printUsage() {
        System.err.println("USAGE: java -jar transform.jar <source-directory>");
        System.err.println("");
        System.err.println("DESCRIPTION:");
        System.err.println("  Applies a generic transformation to migrate classes that have moved");
        System.err.println("  between packages due to breaking API changes.");
        System.err.println("");
        System.err.println("CURRENT TRANSFORMATION:");
        System.err.println("  " + OLD_FQCN + " -> " + NEW_FQCN);
        System.err.println("");
        System.err.println("EXAMPLE:");
        System.err.println("  java -jar transform.jar /workspace/project/src/main/java");
        System.err.println("");
        System.err.println("NOTE: To adapt this transformation for other class relocations,");
        System.err.println("      modify the OLD_FQCN and NEW_FQCN constants in the source code.");
    }
    
    /**
     * Processes a single Java file, applying the transformation.
     * Returns true if the file was modified, false otherwise.
     */
    private static boolean processFile(Path filePath) throws IOException {
        List<String> lines = Files.readAllLines(filePath);
        List<String> newLines = new ArrayList<>();
        boolean modified = false;
        
        for (String line : lines) {
            String newLine = line;
            
            // Handle import statements (most common case)
            if (line.trim().startsWith("import ")) {
                if (line.contains(OLD_FQCN)) {
                    newLine = line.replace(OLD_FQCN, NEW_FQCN);
                    if (!newLine.equals(line)) {
                        System.out.println("  [IMPORT] Updated: " + filePath.getFileName());
                        modified = true;
                    }
                }
            }
            // Handle fully-qualified references in code
            // (e.g., eu.europa.esig.dss.pades.CertificationPermission.MINIMAL_CHANGES_PERMITTED)
            else if (line.contains(OLD_FQCN)) {
                newLine = line.replace(OLD_FQCN, NEW_FQCN);
                if (!newLine.equals(line)) {
                    System.out.println("  [REFERENCE] Updated: " + filePath.getFileName());
                    modified = true;
                }
            }
            
            newLines.add(newLine);
        }
        
        if (modified) {
            Files.write(filePath, newLines);
            return true;
        }
        
        return false;
    }
    
    /**
     * Alternative implementation using regex patterns for more complex cases.
     * This handles edge cases like comments containing the FQCN.
     */
    private static boolean processFileWithRegex(Path filePath) throws IOException {
        String content = Files.readString(filePath);
        String originalContent = content;
        
        // Pattern for imports (handles various whitespace variations)
        Pattern importPattern = Pattern.compile(
            "^\\s*import\\s+" + Pattern.quote(OLD_FQCN) + "\\s*;",
            Pattern.MULTILINE
        );
        
        // Pattern for general references
        Pattern referencePattern = Pattern.compile(
            Pattern.quote(OLD_FQCN),
            Pattern.MULTILINE
        );
        
        boolean modified = false;
        
        // Replace imports
        Matcher importMatcher = importPattern.matcher(content);
        if (importMatcher.find()) {
            content = importMatcher.replaceAll("import " + NEW_FQCN + ";");
            System.out.println("  [REGEX IMPORT] Updated: " + filePath.getFileName());
            modified = true;
        }
        
        // Replace other references (but avoid double-replacing imports)
        Matcher referenceMatcher = referencePattern.matcher(content);
        if (referenceMatcher.find()) {
            content = referenceMatcher.replaceAll(NEW_FQCN);
            // Only count as modified if we haven't already counted it
            if (!modified) {
                System.out.println("  [REGEX REFERENCE] Updated: " + filePath.getFileName());
            }
            modified = true;
        }
        
        if (modified && !content.equals(originalContent)) {
            Files.write(filePath, content.getBytes());
            return true;
        }
        
        return false;
    }
}