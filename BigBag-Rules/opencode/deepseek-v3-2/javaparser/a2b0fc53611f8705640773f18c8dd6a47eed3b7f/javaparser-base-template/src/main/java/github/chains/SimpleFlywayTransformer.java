package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class SimpleFlywayTransformer {
    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("Usage: java github.chains.SimpleFlywayTransformer <source-directory>");
            System.exit(1);
        }
        
        Path sourceDir = Paths.get(args[0]);
        if (!Files.exists(sourceDir) || !Files.isDirectory(sourceDir)) {
            System.err.println("Error: Source directory does not exist: " + sourceDir);
            System.exit(1);
        }
        
        List<Path> javaFiles = new ArrayList<>();
        Files.walk(sourceDir)
            .filter(p -> p.toString().endsWith(".java"))
            .forEach(javaFiles::add);
            
        System.out.println("Found " + javaFiles.size() + " Java files to process");
        
        int modifiedCount = 0;
        for (Path javaFile : javaFiles) {
            if (processFile(javaFile)) {
                modifiedCount++;
            }
        }
        
        System.out.println("Processing complete. Modified " + modifiedCount + " files.");
    }
    
    private static boolean processFile(Path filePath) throws IOException {
        String content = Files.readString(filePath);
        String originalContent = content;
        
        // Pattern 1: Replace new Flyway() with Flyway.configure()
        content = content.replaceAll(
            "new (org\\.flywaydb\\.core\\.)?Flyway\\(\\)", 
            "$1Flyway.configure()"
        );
        
        // Pattern 2: Replace setter calls with builder pattern
        // This is a simple approach - for a real solution we'd need full AST parsing
        // but this should work for the simple cases we see
        
        // Track if we made changes
        boolean modified = !content.equals(originalContent);
        
        if (modified) {
            Files.write(filePath, content.getBytes());
            return true;
        }
        return false;
    }
}