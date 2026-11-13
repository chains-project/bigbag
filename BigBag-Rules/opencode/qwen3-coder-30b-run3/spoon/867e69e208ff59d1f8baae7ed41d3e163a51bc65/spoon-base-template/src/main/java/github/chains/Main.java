package github.chains;

import java.io.*;
import java.nio.file.*;

/**
 * Generic transformation for fixing libthrift 0.16.0 breaking changes
 * Specifically handles multiple API changes:
 * 1. TFramedTransport moved to layered package
 * 2. TTransportException handling
 */
public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source-directory>");
            System.exit(1);
        }

        String sourceDirectory = args[0];
        Path dir = Paths.get(sourceDirectory);
        
        try {
            Files.walk(dir)
                .filter(path -> path.toString().endsWith(".java"))
                .forEach(Main::processJavaFile);
                
            System.out.println("Transformation completed successfully.");
        } catch (IOException e) {
            System.err.println("Error processing files: " + e.getMessage());
            System.exit(1);
        }
    }
    
    private static void processJavaFile(Path filePath) {
        try {
            String content = new String(Files.readAllBytes(filePath));
            String updatedContent = content;
            
            // Fix TFramedTransport imports
            updatedContent = updatedContent.replace(
                "import org.apache.thrift.transport.TFramedTransport;",
                "import org.apache.thrift.transport.layered.TFramedTransport;"
            );
            
            // Fix TFramedTransport usage in code
            updatedContent = updatedContent.replace(
                "org.apache.thrift.transport.TFramedTransport",
                "org.apache.thrift.transport.layered.TFramedTransport"
            );
            
            // Handle TTransportException - make sure it's properly imported
            // TTransportException should remain in transport package
            if (!updatedContent.contains("import org.apache.thrift.transport.TTransportException;")) {
                // Add the import before the first import statement that's not from java or javax packages
                updatedContent = updatedContent.replaceFirst(
                    "(import java\\.[^;]+;|import javax\\.[^;]+;)+",
                    "$1\nimport org.apache.thrift.transport.TTransportException;"
                );
            }
            
            // In case there are any remaining problematic imports
            updatedContent = updatedContent.replaceAll(
                "import org\\.apache\\.thrift\\.transport\\.TTransportException;",
                "import org.apache.thrift.transport.TTransportException;"
            );
            
            if (!content.equals(updatedContent)) {
                Files.write(filePath, updatedContent.getBytes());
                System.out.println("Updated: " + filePath);
            }
        } catch (IOException e) {
            System.err.println("Error processing file " + filePath + ": " + e.getMessage());
        }
    }
}