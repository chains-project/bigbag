package github.chains;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Generic transformation rule for fixing Maven dependency breaking changes in doxia-site-renderer.
 * 
 * This transformation fixes the breaking change where RenderingContext was moved from:
 *   org.apache.maven.doxia.module.xhtml.decoration.render.RenderingContext
 * to:
 *   org.apache.maven.doxia.siterenderer.RenderingContext
 * 
 * The transformation identifies:
 * 1. Import statements that reference the old package
 * 2. Constructor calls that use the old RenderingContext class
 * 
 * Usage: java -cp target/classes:target/dependency/* github.chains.Main /path/to/project/src
 */
public class Main {
    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Usage: java github.chains.Main <source_directory>");
            System.exit(1);
        }
        
        String sourceDirectory = args[0];
        try {
            processDirectory(sourceDirectory);
        } catch (IOException e) {
            System.err.println("Error processing directory: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private static void processDirectory(String directoryPath) throws IOException {
        Path dir = Paths.get(directoryPath);
        if (!Files.exists(dir)) {
            System.err.println("Directory does not exist: " + directoryPath);
            return;
        }
        
        List<Path> javaFiles = Files.walk(dir)
                .filter(Files::isRegularFile)
                .filter(path -> path.toString().endsWith(".java"))
                .collect(Collectors.toList());
        
        for (Path javaFile : javaFiles) {
            try {
                processJavaFile(javaFile);
            } catch (Exception e) {
                System.err.println("Error processing " + javaFile + ": " + e.getMessage());
                e.printStackTrace();
            }
        }
    }
    
    private static void processJavaFile(Path javaFilePath) throws IOException {
        String content = Files.readString(javaFilePath);
        
        // Check for the old import and replace it with the new one
        boolean hasChanges = false;
        String newContent = content;
        
        // Replace import statement
        String oldImport = "import org.apache.maven.doxia.module.xhtml.decoration.render.RenderingContext;";
        String newImport = "import org.apache.maven.doxia.siterenderer.RenderingContext;";
        if (content.contains(oldImport)) {
            newContent = newContent.replace(oldImport, newImport);
            System.out.println("  Updated import: " + oldImport);
            hasChanges = true;
        }
        
        // Replace constructor calls
        String oldConstructor = "new RenderingContext(";
        String newConstructor = "new org.apache.maven.doxia.siterenderer.RenderingContext(";
        if (content.contains(oldConstructor)) {
            newContent = newContent.replace(oldConstructor, newConstructor);
            System.out.println("  Updated constructor call");
            hasChanges = true;
        }
        
        // Save the modified file if changes were made
        if (hasChanges) {
            Files.writeString(javaFilePath, newContent);
            System.out.println("Fixed: " + javaFilePath);
        }
    }
}