package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.NodeList;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.Path;
import java.io.IOException;

public class Main {
    public static void main(String[] args) throws IOException {
        // Process all Java files in the target directory
        String targetDir = args.length > 0 ? args[0] : "/workspace/fluxtion";
        Path dirPath = Paths.get(targetDir);
        
        if (!Files.exists(dirPath)) {
            System.err.println("Target directory does not exist: " + targetDir);
            return;
        }
        
        // Walk through all Java files
        Files.walk(dirPath)
            .filter(path -> path.toString().endsWith(".java"))
            .forEach(Main::processFile);
    }
    
    private static void processFile(Path filePath) {
        try {
            // Parse the Java file
            CompilationUnit cu = StaticJavaParser.parse(filePath.toFile());
            
            // Looking for specific pattern: new Constructor() 
            // We'll try to replace it more manually with string replacement
            String content = new String(Files.readAllBytes(filePath));
            boolean modified = false;
            
            // Pattern matching approach for Constructor() calls without arguments
            // Looking for constructor calls to SnakeYAML classes without parameters 
            String[] patterns = {
                "new Constructor()", 
                "new SafeConstructor()",
                "new CustomClassLoaderConstructor()"
            };
            
            for (String pattern : patterns) {
                if (content.contains(pattern)) {
                    // Replace with proper Constructor call
                    String replacement = "";
                    if (pattern.equals("new Constructor()")) {
                        replacement = "new Constructor(new LoaderOptions())";
                    } else if (pattern.equals("new SafeConstructor()")) {
                        replacement = "new SafeConstructor(new LoaderOptions())";
                    } else if (pattern.equals("new CustomClassLoaderConstructor()")) {
                        replacement = "new CustomClassLoaderConstructor(new LoaderOptions())";
                    }
                    
                    content = content.replace(pattern, replacement);
                    modified = true;
                }
            }
            
            if (modified) {
                Files.write(filePath, content.getBytes());
            }
            
        } catch (Exception e) {
            System.err.println("Error processing file " + filePath + ": " + e.getMessage());
        }
    }
}
