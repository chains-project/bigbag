package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Generic transformation to fix MySQL JDBC exception packages from com.mysql.jdbc.exceptions to com.mysql.cj.exceptions
 */
public class Main {
    
    public static void main(String[] args) {
        try {
            String sourceDir = args.length > 0 ? args[0] : "/workspace/pdb/src";
            
            Files.walk(Paths.get(sourceDir))
                .filter(path -> path.toString().endsWith(".java"))
                .forEach(Main::processFile);
                
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    private static void processFile(Path filePath) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(filePath.toFile());
            
            // Replace all occurrences of the old package with the new one
            String content = cu.toString();
            content = content.replace("com.mysql.jdbc.exceptions", "com.mysql.cj.exceptions");
            
            // Save the modified file
            Files.write(filePath, content.getBytes());
            System.out.println("Processed: " + filePath);
            
        } catch (Exception e) {
            System.err.println("Failed to process " + filePath + ": " + e.getMessage());
        }
    }
}
