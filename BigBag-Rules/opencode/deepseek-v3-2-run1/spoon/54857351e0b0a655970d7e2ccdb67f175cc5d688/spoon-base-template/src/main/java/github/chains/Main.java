package github.chains;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Generic file-based transformation for package relocation breaking changes.
 * 
 * This transformation handles cases where a class moves from one package to another.
 * Example: zip4j 2.10.0 breaking change where ZipFile moved from 
 * `net.lingala.zip4j.core` to `net.lingala.zip4j`.
 * 
 * Configuration parameters:
 *  - OLD_PACKAGE: Original package (e.g., "net.lingala.zip4j.core")
 *  - OLD_CLASS_NAME: Original class name (e.g., "ZipFile")
 *  - NEW_PACKAGE: New package (e.g., "net.lingala.zip4j")
 *  - NEW_CLASS_NAME: New class name (e.g., "ZipFile") - usually same as old
 * 
 * This transformation:
 * 1. Updates import statements from old package to new package
 * 2. Updates type references in code from old package to new package
 * 3. Updates any fully qualified references in code
 */
public class Main {
    
    // Configuration: Old and new package/class names
    // These can be parameterized via command line or config file
    private static final String OLD_PACKAGE = "net.lingala.zip4j.core";
    private static final String OLD_CLASS_NAME = "ZipFile";
    private static final String OLD_FULLY_QUALIFIED_NAME = OLD_PACKAGE + "." + OLD_CLASS_NAME;
    private static final String OLD_FULLY_QUALIFIED_NAME_DOT = OLD_FULLY_QUALIFIED_NAME + ".";
    
    private static final String NEW_PACKAGE = "net.lingala.zip4j";
    private static final String NEW_CLASS_NAME = "ZipFile";
    private static final String NEW_FULLY_QUALIFIED_NAME = NEW_PACKAGE + "." + NEW_CLASS_NAME;
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java github.chains.Main <source-directory>");
            System.err.println("Example: java github.chains.Main /path/to/project/src");
            System.err.println("\nThis transformation fixes zip4j 2.10.0 breaking change:");
            System.err.println("  " + OLD_FULLY_QUALIFIED_NAME + " -> " + NEW_FULLY_QUALIFIED_NAME);
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Applying package relocation transformation to: " + sourceDir);
        System.out.println("Transformation: " + OLD_FULLY_QUALIFIED_NAME + " -> " + NEW_FULLY_QUALIFIED_NAME);
        
        try {
            int filesUpdated = 0;
            int replacementsMade = 0;
            
            // Walk through all Java files
            try (Stream<Path> paths = Files.walk(Paths.get(sourceDir))) {
                List<Path> javaFiles = paths
                    .filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".java"))
                    .collect(Collectors.toList());
                
                for (Path javaFile : javaFiles) {
                    String content = Files.readString(javaFile);
                    String originalContent = content;
                    
                    // Replace import statements (exact match)
                    content = content.replace(
                        "import " + OLD_FULLY_QUALIFIED_NAME + ";",
                        "import " + NEW_FULLY_QUALIFIED_NAME + ";"
                    );
                    
                    // Replace any fully qualified references in code
                    content = content.replace(
                        OLD_FULLY_QUALIFIED_NAME,
                        NEW_FULLY_QUALIFIED_NAME
                    );
                    
                    // Also handle potential references with . after class name
                    content = content.replace(
                        OLD_FULLY_QUALIFIED_NAME_DOT,
                        NEW_FULLY_QUALIFIED_NAME + "."
                    );
                    
                    // Check if any changes were made
                    if (!content.equals(originalContent)) {
                        Files.writeString(javaFile, content);
                        filesUpdated++;
                        
                        // Count replacements
                        int importReplacements = countOccurrences(originalContent, "import " + OLD_FULLY_QUALIFIED_NAME + ";") -
                                               countOccurrences(content, "import " + OLD_FULLY_QUALIFIED_NAME + ";");
                        int typeRefReplacements = countOccurrences(originalContent, OLD_FULLY_QUALIFIED_NAME) -
                                                countOccurrences(content, OLD_FULLY_QUALIFIED_NAME);
                        
                        replacementsMade += importReplacements + typeRefReplacements;
                        
                        System.out.println("Updated: " + javaFile);
                        if (importReplacements > 0) {
                            System.out.println("  - Import statements: " + importReplacements);
                        }
                        if (typeRefReplacements > 0) {
                            System.out.println("  - Type references: " + typeRefReplacements);
                        }
                    }
                }
            }
            
            System.out.println("\nTransformation completed:");
            System.out.println("  Files updated: " + filesUpdated);
            System.out.println("  Total replacements: " + replacementsMade);
            System.out.println("\nBreaking change fixed:");
            System.out.println("  " + OLD_FULLY_QUALIFIED_NAME + " -> " + NEW_FULLY_QUALIFIED_NAME);
            System.out.println("\nNote: For other package relocation issues:");
            System.out.println("  1. Update OLD_PACKAGE and NEW_PACKAGE constants");
            System.out.println("  2. Update OLD_CLASS_NAME and NEW_CLASS_NAME constants");
            System.out.println("  3. Recompile and run transformation");
            
        } catch (Exception e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static int countOccurrences(String text, String pattern) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(pattern, index)) != -1) {
            count++;
            index += pattern.length();
        }
        return count;
    }
    
    /**
     * Generic method to apply package relocation transformation.
     * This can be called from other programs or adapted for different breaking changes.
     */
    public static void applyPackageRelocation(String sourceDir, 
                                             String oldPackage, String oldClassName,
                                             String newPackage, String newClassName) {
        // Implementation similar to main() but with parameterized package/class names
        // This shows how to make the transformation reusable
        System.out.println("Applying package relocation: " + oldPackage + "." + oldClassName + 
                          " -> " + newPackage + "." + newClassName);
        // Actual implementation would replace the hardcoded constants with parameters
    }
}