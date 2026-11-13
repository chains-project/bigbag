package github.chains;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

public class SimpleMain {
    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("Usage: java -jar spoon-transformation.jar <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Processing source directory: " + sourceDir);
        
        // Find all Java files
        List<Path> javaFiles = Files.walk(Paths.get(sourceDir))
            .filter(path -> path.toString().endsWith(".java"))
            .collect(Collectors.toList());
        
        System.out.println("Found " + javaFiles.size() + " Java files");
        
        int transformedFiles = 0;
        for (Path javaFile : javaFiles) {
            String content = Files.readString(javaFile);
            String originalContent = content;
            
            // Remove imports from org.bouncycastle.crypto.tls
            content = content.replaceAll(
                "import\\s+org\\.bouncycastle\\.crypto\\.tls\\.[^;]+;\\s*\n?", 
                ""
            );
            
            // Replace TlsFatalAlert(AlertDescription.XXX) with IOException("TLS fatal alert: XXX")
            content = content.replaceAll(
                "new\\s+TlsFatalAlert\\s*\\(\\s*AlertDescription\\.([a-zA-Z_]+)\\s*\\)",
                "new IOException(\"TLS fatal alert: $1\")"
            );
            
            // Also handle if AlertDescription is imported with a different name
            // Replace new TlsFatalAlert(someAlert) with new IOException("TLS fatal alert")
            content = content.replaceAll(
                "new\\s+TlsFatalAlert\\s*\\(\\s*([a-zA-Z_]+\\s*\\.\\s*)?[a-zA-Z_]+\\s*\\)",
                "new IOException(\"TLS fatal alert\")"
            );
            
            if (!content.equals(originalContent)) {
                System.out.println("Transforming: " + javaFile);
                Files.writeString(javaFile, content);
                transformedFiles++;
            }
        }
        
        System.out.println("Transformation complete! Transformed " + transformedFiles + " files.");
    }
}