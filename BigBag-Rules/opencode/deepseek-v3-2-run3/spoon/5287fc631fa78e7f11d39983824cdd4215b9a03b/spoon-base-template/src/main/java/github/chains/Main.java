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
 * Generic transformation rule for Jetty 8.x to 11.x migration.
 * 
 * This transformation handles the breaking change where SelectChannelConnector
 * was removed and replaced with ServerConnector in Jetty 11.x.
 * 
 * Transformation rules:
 * 1. new SelectChannelConnector() -> new ServerConnector(server)
 * 2. Import updates: org.eclipse.jetty.server.nio.SelectChannelConnector -> org.eclipse.jetty.server.ServerConnector
 * 3. Field type updates: Connector -> ServerConnector for fields that were assigned SelectChannelConnector
 * 
 * Usage: java Main <source-directory>
 */
public class Main {
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source-directory>");
            System.err.println("Transforms Jetty 8.x SelectChannelConnector usage to Jetty 11.x ServerConnector");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        
        try {
            System.out.println("Transforming Jetty SelectChannelConnector -> ServerConnector in: " + sourceDir);
            
            // Find all Java files
            List<Path> javaFiles = findJavaFiles(sourceDir);
            System.out.println("Found " + javaFiles.size() + " Java files");
            
            int transformedFiles = 0;
            int totalChanges = 0;
            
            for (Path file : javaFiles) {
                TransformationResult result = transformFile(file);
                if (result.transformed) {
                    transformedFiles++;
                    totalChanges += result.changeCount;
                    System.out.println("Transformed " + file + " (" + result.changeCount + " changes)");
                }
            }
            
            System.out.println("\nTransformation complete!");
            System.out.println("Files transformed: " + transformedFiles);
            System.out.println("Total changes made: " + totalChanges);
            System.out.println("\nSummary of changes applied:");
            System.out.println("1. Constructor replacement: new SelectChannelConnector() -> new ServerConnector(server)");
            System.out.println("2. Import updates: org.eclipse.jetty.server.nio.SelectChannelConnector -> org.eclipse.jetty.server.ServerConnector");
            System.out.println("3. Field type updates: Connector -> ServerConnector for relevant fields");
            System.out.println("\nNote: Additional API changes may be needed:");
            System.out.println("- Server.setSendServerVersion()/setSendDateHeader() moved to HttpConfiguration");
            System.out.println("- AbstractHandler.handle() signature changed");
            System.out.println("- Check Jetty 11.x API documentation for other changes");
            
        } catch (Exception e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static List<Path> findJavaFiles(String sourceDir) throws IOException {
        try (Stream<Path> paths = Files.walk(Paths.get(sourceDir))) {
            return paths
                .filter(Files::isRegularFile)
                .filter(p -> p.toString().endsWith(".java"))
                .collect(Collectors.toList());
        }
    }
    
    private static TransformationResult transformFile(Path file) throws IOException {
        String content = Files.readString(file);
        String originalContent = content;
        int changeCount = 0;
        
        // 1. Replace constructor calls: new SelectChannelConnector() -> new ServerConnector(server)
        String newContent = content.replaceAll(
            "new\\s+SelectChannelConnector\\s*\\(\\s*\\)", 
            "new ServerConnector(server)"
        );
        if (!newContent.equals(content)) {
            changeCount++;
            content = newContent;
        }
        
        // 2. Replace imports
        newContent = content.replace(
            "import org.eclipse.jetty.server.nio.SelectChannelConnector;",
            "import org.eclipse.jetty.server.ServerConnector;"
        );
        if (!newContent.equals(content)) {
            changeCount++;
            content = newContent;
        }
        
        // 3. Handle wildcard imports
        newContent = content.replace(
            "import org.eclipse.jetty.server.nio.*;",
            "import org.eclipse.jetty.server.*;"
        );
        if (!newContent.equals(content)) {
            changeCount++;
            content = newContent;
        }
        
        // 4. Update field declarations from Connector to ServerConnector 
        // Pattern: "private final Connector httpConnector = new SelectChannelConnector"
        newContent = content.replaceAll(
            "(private\\s+(final\\s+)?)Connector\\s+(\\w+)\\s*=\\s*new\\s+SelectChannelConnector",
            "$1ServerConnector $3 = new ServerConnector"
        );
        if (!newContent.equals(content)) {
            changeCount++;
            content = newContent;
        }
        
        // 5. Update field declarations without assignment but likely to be ServerConnector
        // This is heuristic - only update if we see ServerConnector usage in the file
        if (content.contains("ServerConnector") && !content.contains("import org.eclipse.jetty.server.ServerConnector;")) {
            // Add import if needed
            newContent = content.replaceFirst(
                "(package\\s+[^;]+;\\s*)",
                "$1\nimport org.eclipse.jetty.server.ServerConnector;"
            );
            if (!newContent.equals(content)) {
                changeCount++;
                content = newContent;
            }
        }
        
        // Check if file was modified
        if (!content.equals(originalContent)) {
            Files.writeString(file, content);
            return new TransformationResult(true, changeCount);
        }
        
        return new TransformationResult(false, 0);
    }
    
    private static class TransformationResult {
        final boolean transformed;
        final int changeCount;
        
        TransformationResult(boolean transformed, int changeCount) {
            this.transformed = transformed;
            this.changeCount = changeCount;
        }
    }
}