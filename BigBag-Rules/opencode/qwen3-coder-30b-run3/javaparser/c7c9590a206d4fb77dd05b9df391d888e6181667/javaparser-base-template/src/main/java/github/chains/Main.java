package github.chains;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/**
 * Generic transformation rule for fixing Maven plugin compilation errors caused by breaking dependency updates
 * This transformer fixes outdated import statements that reference classes moved or removed in dependency upgrades
 */
public class Main {
    public static void main(String[] args) {
        // Usage: java Main /path/to/source/codebase
        if (args.length < 1) {
            System.err.println("Usage: java Main <source_directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        try {
            // Process Java files recursively
            Files.walk(Paths.get(sourceDir))
                .filter(path -> path.toString().endsWith(".java"))
                .forEach(Main::processJavaFile);
        } catch (IOException e) {
            System.err.println("Error processing files: " + e.getMessage());
            System.exit(1);
        }
    }
    
    private static void processJavaFile(Path javaFilePath) {
        try {
            // Read the file content
            String content = new String(Files.readAllBytes(javaFilePath), StandardCharsets.UTF_8);
            
            // Apply transformation to fix broken imports
            String modifiedContent = fixBrokenImports(content);
            
            if (!content.equals(modifiedContent)) {
                // Write back the modified content
                Files.write(javaFilePath, modifiedContent.getBytes(StandardCharsets.UTF_8));
                System.out.println("Fixed imports in: " + javaFilePath);
            }
        } catch (Exception e) {
            System.err.println("Error processing " + javaFilePath + ": " + e.getMessage());
        }
    }
    
    /**
     * Generic method to fix broken imports that are commonly affected by dependency updates
     * Specifically addresses the case where classes were moved from one package to another
     */
    private static String fixBrokenImports(String content) {
        // Define the mapping of old package paths to new package paths
        // These are the common patterns that cause compilation errors in Maven plugins
        Map<String, String> importMappings = new HashMap<>();
        
        // For the specific case we're targeting: doxia-site-renderer package changes
        importMappings.put("import org.apache.maven.doxia.module.xhtml.decoration.render.RenderingContext;",
                          "import org.apache.maven.doxia.siterenderer.RenderingContext;");
        
        // More generic mappings for common dependency breakages
        importMappings.put("import org.apache.maven.doxia.module.xhtml.decoration.render.DocumentContent;",
                          "import org.apache.maven.doxia.siterenderer.DocumentContent;");
        importMappings.put("import org.apache.maven.doxia.module.xhtml.decoration.render.SiteRenderingContext;",
                          "import org.apache.maven.doxia.siterenderer.SiteRenderingContext;");
        importMappings.put("import org.apache.maven.doxia.module.xhtml.decoration.render.Renderer;",
                          "import org.apache.maven.doxia.siterenderer.Renderer;");
        importMappings.put("import org.apache.maven.doxia.module.xhtml.decoration.render.DocumentRenderer;",
                          "import org.apache.maven.doxia.siterenderer.DocumentRenderer;");
        importMappings.put("import org.apache.maven.doxia.module.xhtml.decoration.render.DefaultSiteRenderer;",
                          "import org.apache.maven.doxia.siterenderer.DefaultSiteRenderer;");
        
        String modifiedContent = content;
        
        // Check each import mapping and replace
        for (Map.Entry<String, String> entry : importMappings.entrySet()) {
            if (modifiedContent.contains(entry.getKey())) {
                modifiedContent = modifiedContent.replace(entry.getKey(), entry.getValue());
            }
        }
        
        return modifiedContent;
    }
}
