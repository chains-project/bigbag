package github.chains;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * GENERIC TRANSFORMATION RULE for Jakarta EE namespace migration.
 * 
 * BREAKING CHANGE PATTERN ANALYSIS:
 * ================================
 * Old API Pattern: javax.{subpackage}.{ClassName}
 * New API Pattern: jakarta.{subpackage}.{ClassName}
 * 
 * Structural Transformation Required:
 * 1. Import statements: "import javax.servlet.Filter;" -> "import jakarta.servlet.Filter;"
 * 2. Fully-qualified type names in code: "javax.servlet.Filter f;" -> "jakarta.servlet.Filter f;"
 * 3. Static imports: "import static javax.servlet.DispatcherType.*;" -> "import static jakarta.servlet.DispatcherType.*;"
 * 
 * Scope: This transformation applies to ANY Java project migrating from
 *        Jakarta EE 8 (javax.*) to Jakarta EE 9+ (jakarta.*).
 * 
 * Generic Parameters:
 * - OLD_PREFIX: "javax"
 * - NEW_PREFIX: "jakarta"  
 * - SUBPACKAGES: servlet, validation, annotation, ejb, persistence, transaction, ws.rs, xml.bind
 * 
 * REUSABILITY: This transformation is parameterized and can be applied to
 * any project by simply changing the source directory path. No project-specific
 * identifiers are hardcoded.
 */
public class Main {
    
    // CONFIGURATION: Map of old package prefixes to new package prefixes
    // Extend this array for additional javax.* -> jakarta.* migrations
    private static final String[][] PACKAGE_MAPPINGS = {
        // Jakarta EE Core migrations
        {"javax.servlet", "jakarta.servlet"},
        {"javax.validation", "jakarta.validation"},
        {"javax.annotation", "jakarta.annotation"},
        {"javax.ejb", "jakarta.ejb"},
        {"javax.persistence", "jakarta.persistence"},
        {"javax.transaction", "jakarta.transaction"},
        {"javax.ws.rs", "jakarta.ws.rs"},
        {"javax.xml.bind", "jakarta.xml.bind"},
        {"javax.activation", "jakarta.activation"},
        {"javax.inject", "jakarta.inject"},
        {"javax.jms", "jakarta.jms"},
        {"javax.json", "jakarta.json"},
        {"javax.mail", "jakarta.mail"},
        {"javax.resource", "jakarta.resource"},
        {"javax.security.auth.message", "jakarta.security.auth.message"},
        {"javax.security.enterprise", "jakarta.security.enterprise"},
        {"javax.servlet.http", "jakarta.servlet.http"},
        {"javax.servlet.jsp", "jakarta.servlet.jsp"},
        {"javax.servlet.jsp.el", "jakarta.servlet.jsp.el"},
        {"javax.servlet.jsp.tagext", "jakarta.servlet.jsp.tagext"},
        {"javax.websocket", "jakarta.websocket"}
    };
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source-directory>");
            System.err.println("Example: java Main /path/to/project/src/main/java");
            System.err.println("\nThis transformation handles Jakarta EE namespace migration:");
            System.err.println("  javax.* -> jakarta.*");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("=== Jakarta EE Namespace Migration Transformation ===");
        System.out.println("Applying to: " + sourceDir);
        System.out.println("\nBreaking Change Pattern:");
        System.out.println("  Old: javax.{package}.{Type}");
        System.out.println("  New: jakarta.{package}.{Type}");
        System.out.println("\nPackage mappings configured: " + PACKAGE_MAPPINGS.length);
        
        try {
            int totalChanges = processDirectory(sourceDir);
            System.out.println("\n=== Transformation Complete ===");
            System.out.println("Total files modified: " + totalChanges);
            System.out.println("\nNOTE: This is a structural transformation only.");
            System.out.println("      You may need to update dependency versions separately.");
            
        } catch (Exception e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static int processDirectory(String dirPath) throws Exception {
        int fileCount = 0;
        File dir = new File(dirPath);
        
        if (!dir.exists() || !dir.isDirectory()) {
            System.err.println("Error: " + dirPath + " is not a valid directory");
            return 0;
        }
        
        List<File> javaFiles = findJavaFiles(dir);
        System.out.println("\nFound " + javaFiles.size() + " Java files to process");
        
        for (File javaFile : javaFiles) {
            if (processFile(javaFile)) {
                fileCount++;
            }
        }
        
        return fileCount;
    }
    
    private static List<File> findJavaFiles(File dir) throws Exception {
        try (java.util.stream.Stream<Path> walk = Files.walk(dir.toPath())) {
            return walk.filter(path -> path.toString().endsWith(".java"))
                .map(Path::toFile)
                .collect(java.util.stream.Collectors.toList());
        }
    }
    
    private static boolean processFile(File file) {
        try {
            String content = new String(Files.readAllBytes(file.toPath()));
            String originalContent = content;
            int changeCount = 0;
            
            // Apply all package mappings
            for (String[] mapping : PACKAGE_MAPPINGS) {
                String oldPrefix = mapping[0];
                String newPrefix = mapping[1];
                
                // Pattern 1: Regular imports
                String importPattern = "import\\s+" + oldPrefix.replace(".", "\\.") + "\\.";
                String replacement = "import " + newPrefix + ".";
                String newContent = content.replaceAll(importPattern, replacement);
                if (!newContent.equals(content)) {
                    changeCount++;
                    content = newContent;
                }
                
                // Pattern 2: Static imports
                String staticImportPattern = "import\\s+static\\s+" + oldPrefix.replace(".", "\\.") + "\\.";
                String staticReplacement = "import static " + newPrefix + ".";
                newContent = content.replaceAll(staticImportPattern, staticReplacement);
                if (!newContent.equals(content)) {
                    changeCount++;
                    content = newContent;
                }
                
                // Pattern 3: Fully-qualified type names in code
                // More careful regex to avoid matching partial words
                String fqPattern = "\\b" + oldPrefix.replace(".", "\\.") + "\\.";
                newContent = content.replaceAll(fqPattern, newPrefix + ".");
                if (!newContent.equals(content)) {
                    changeCount++;
                    content = newContent;
                }
            }
            
            // Only write if changes were made
            if (!content.equals(originalContent)) {
                Files.write(file.toPath(), content.getBytes());
                System.out.println("  Transformed: " + file.getPath() + " (" + changeCount + " changes)");
                return true;
            }
            
            return false;
            
        } catch (Exception e) {
            System.err.println("Error processing file " + file.getPath() + ": " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Returns the transformation description for documentation purposes.
     */
    public static String getTransformationDescription() {
        StringBuilder sb = new StringBuilder();
        sb.append("TRANSFORMATION: Jakarta EE Namespace Migration\n");
        sb.append("PATTERN: javax.{package}.{Type} -> jakarta.{package}.{Type}\n");
        sb.append("SCOPE: ").append(PACKAGE_MAPPINGS.length).append(" package mappings\n");
        sb.append("MAPPINGS:\n");
        for (String[] mapping : PACKAGE_MAPPINGS) {
            sb.append("  ").append(mapping[0]).append(" -> ").append(mapping[1]).append("\n");
        }
        return sb.toString();
    }
}