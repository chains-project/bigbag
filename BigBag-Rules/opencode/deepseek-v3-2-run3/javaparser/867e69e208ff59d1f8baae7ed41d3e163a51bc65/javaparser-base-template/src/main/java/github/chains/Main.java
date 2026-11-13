package github.chains;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Main {
    
    // Configuration: old and new fully-qualified type names
    // This transformation handles package migration for classes that moved between versions
    // Example: TFramedTransport moved from org.apache.thrift.transport to org.apache.thrift.transport.layered
    private static final String OLD_TYPE = "org.apache.thrift.transport.TFramedTransport";
    private static final String NEW_TYPE = "org.apache.thrift.transport.layered.TFramedTransport";
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java github.chains.Main <source-directory>");
            System.err.println("Transforms: " + OLD_TYPE + " -> " + NEW_TYPE);
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Transforming " + OLD_TYPE + " to " + NEW_TYPE);
        System.out.println("Processing directory: " + sourceDir);
        
        try {
            processDirectory(Paths.get(sourceDir));
            System.out.println("Transformation completed successfully.");
        } catch (IOException e) {
            System.err.println("Error processing files: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    /**
     * Process all Java files in the directory recursively.
     * This transformation handles:
     * 1. Import statements: import org.apache.thrift.transport.TFramedTransport;
     * 2. Fully-qualified type references: org.apache.thrift.transport.TFramedTransport
     * 
     * Note: Simple name references (TFramedTransport) will work after import is updated.
     */
    private static void processDirectory(Path directory) throws IOException {
        Files.walk(directory)
            .filter(path -> path.toString().endsWith(".java"))
            .forEach(Main::processFile);
    }
    
    private static void processFile(Path filePath) {
        try {
            String content = new String(Files.readAllBytes(filePath));
            String transformed = content.replaceAll(
                "org\\.apache\\.thrift\\.transport\\.TFramedTransport", 
                "org.apache.thrift.transport.layered.TFramedTransport");
            
            if (!transformed.equals(content)) {
                Files.write(filePath, transformed.getBytes());
                System.out.println("Modified: " + filePath);
            }
        } catch (IOException e) {
            System.err.println("Error processing file " + filePath + ": " + e.getMessage());
        }
    }
    
    /**
     * Generic transformation rule for package migration:
     * When a class moves from one package to another in a dependency update,
     * this transformation updates all import statements and fully-qualified
     * references to use the new package.
     * 
     * Pattern: Old API: FullyQualifiedOldType
     *          New API: FullyQualifiedNewType
     * Transformation: Replace all occurrences of FullyQualifiedOldType with FullyQualifiedNewType
     * 
     * This handles:
     * - Import statements
     * - Fully-qualified type references in code
     * - Type references in extends/implements clauses
     * - Variable declarations, method parameters, return types
     * 
     * Limitations:
     * - Does not handle method signature changes (parameter types, exceptions, new methods)
     * - Does not handle constructor signature changes
     * - Simple name references rely on import statement updates
     */
}