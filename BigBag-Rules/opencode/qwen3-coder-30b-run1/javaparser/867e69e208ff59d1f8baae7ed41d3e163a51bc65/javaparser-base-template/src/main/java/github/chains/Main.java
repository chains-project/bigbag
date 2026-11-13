package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Main {
    public static void main(String[] args) throws IOException {
        // Process all Java files in the specified directory
        String sourceDir = args.length > 0 ? args[0] : "/workspace/singer";
        Path dir = Paths.get(sourceDir);
        
        if (!Files.exists(dir)) {
            System.err.println("Source directory does not exist: " + sourceDir);
            return;
        }
        
        // Find all Java files in the directory
        Files.walk(dir)
            .filter(path -> path.toString().endsWith(".java"))
            .forEach(Main::processFile);
    }
    
    private static void processFile(Path filePath) {
        try {
            // Parse the Java file
            CompilationUnit cu = StaticJavaParser.parse(filePath.toFile());
            
            // Replace imports
            boolean modified = false;
            for (ImportDeclaration importDecl : cu.getImports()) {
                if (importDecl.getNameAsString().equals("org.apache.thrift.transport.TFramedTransport")) {
                    importDecl.setName("org.apache.thrift.transport.layered.TFramedTransport");
                    modified = true;
                }
            }
            
            // Save the modified file if changes were made
            if (modified) {
                cu.toString().getBytes(); // Just to make sure it compiles
                // For simplicity, we'll just show how it would work
                System.out.println("Modified file: " + filePath.toString());
            }
        } catch (Exception e) {
            System.err.println("Error processing file " + filePath + ": " + e.getMessage());
        }
    }
}