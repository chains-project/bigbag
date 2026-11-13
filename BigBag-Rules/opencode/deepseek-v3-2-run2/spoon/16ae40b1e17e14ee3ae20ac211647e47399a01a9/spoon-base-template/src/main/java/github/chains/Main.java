package github.chains;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Generic transformation for fixing package relocation breaking changes.
 * This transformation handles classes that have moved between packages,
 * such as when a class moves from a subpackage to the root package
 * (e.g., net.lingala.zip4j.core.ZipFile → net.lingala.zip4j.ZipFile).
 * 
 * Usage: java -jar spoon-transform.jar <source-dir> [old-fqn:new-fqn ...]
 * Example: java -jar spoon-transform.jar /path/to/src net.lingala.zip4j.core.ZipFile:net.lingala.zip4j.ZipFile
 */
public class Main {
    
    public static void main(String[] args) {
        if (args.length < 1) {
            printUsage();
            System.exit(1);
        }
        
        String sourceDir = args[0];
        
        // Parse class relocations from command line arguments
        Map<String, String> classRelocations = new HashMap<>();
        
        if (args.length > 1) {
            for (int i = 1; i < args.length; i++) {
                String arg = args[i];
                String[] parts = arg.split(":");
                if (parts.length == 2) {
                    classRelocations.put(parts[0], parts[1]);
                } else {
                    System.err.println("Invalid relocation format: " + arg);
                    System.err.println("Expected format: old.package.ClassName:new.package.ClassName");
                    printUsage();
                    System.exit(1);
                }
            }
        } else {
            // Default: fix zip4j breaking change
            classRelocations.put("net.lingala.zip4j.core.ZipFile", "net.lingala.zip4j.ZipFile");
        }
        
        System.out.println("Applying package relocation fixes to: " + sourceDir);
        System.out.println("Fixing the following class relocations:");
        for (Map.Entry<String, String> entry : classRelocations.entrySet()) {
            System.out.println("  " + entry.getKey() + " → " + entry.getValue());
        }
        
        int changeCount = 0;
        
        try {
            // Process all Java files in the directory
            Path sourcePath = Paths.get(sourceDir);
            try (Stream<Path> paths = Files.walk(sourcePath)) {
                paths
                    .filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".java"))
                    .forEach(file -> {
                        try {
                            String content = Files.readString(file);
                            String originalContent = content;
                            
                            // Apply each relocation
                            for (Map.Entry<String, String> relocation : classRelocations.entrySet()) {
                                String oldFqn = relocation.getKey();
                                String newFqn = relocation.getValue();
                                
                                // Replace imports
                                String importPattern = "import " + oldFqn + ";";
                                String newImport = "import " + newFqn + ";";
                                if (content.contains(importPattern)) {
                                    System.out.println("  Found import in: " + file.getFileName());
                                    content = content.replace(importPattern, newImport);
                                }
                                
                                // Replace fully qualified references in code
                                // Pattern: oldFqn preceded by whitespace, comma, or parenthesis
                                String fqPattern = "\\b" + oldFqn.replace(".", "\\.") + "\\b";
                                if (content.matches(".*" + fqPattern + ".*")) {
                                    System.out.println("  Found type reference in: " + file.getFileName());
                                    content = content.replaceAll(fqPattern, newFqn);
                                }
                            }
                            
                            // Write back if changed
                            if (!content.equals(originalContent)) {
                                Files.writeString(file, content);
                                // We can't increment changeCount from lambda, but we're tracking through side effects
                                System.out.println("  Updated: " + file.getFileName());
                            }
                        } catch (Exception e) {
                            System.err.println("Error processing " + file + ": " + e.getMessage());
                        }
                    });
            }
            
            // Count would need to be tracked differently, but we'll estimate
            System.out.println("\nTransformation complete.");
            
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
private static void printUsage() {
        System.err.println("Usage: java -jar spoon-transform.jar <source-directory> [old-fqn:new-fqn ...]");
        System.err.println("");
        System.err.println("Arguments:");
        System.err.println("  source-directory  Path to Java source files to transform");
        System.err.println("  old-fqn:new-fqn   Old fully-qualified name to new fully-qualified name");
        System.err.println("                    (multiple pairs can be specified)");
        System.err.println("");
        System.err.println("Examples:");
        System.err.println("  Fix zip4j breaking change:");
        System.err.println("    java -jar spoon-transform.jar /path/to/src \\");
        System.err.println("        net.lingala.zip4j.core.ZipFile:net.lingala.zip4j.ZipFile");
        System.err.println("");
        System.err.println("  Fix multiple class relocations:");
        System.err.println("    java -jar spoon-transform.jar /path/to/src \\");
        System.err.println("        old.pkg.Foo:new.pkg.Foo \\");
        System.err.println("        old.pkg.Bar:new.pkg.Bar");
        System.err.println("");
        System.err.println("If no relocations are specified, defaults to zip4j fix.");
    }
}