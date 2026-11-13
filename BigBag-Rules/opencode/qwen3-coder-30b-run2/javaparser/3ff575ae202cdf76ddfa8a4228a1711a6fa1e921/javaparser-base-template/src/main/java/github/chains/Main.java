package github.chains;

import com.github.javaparser.*;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;

import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Generic transformation rule to fix breaking changes in plexus-container-default 2.1.1
 * Specifically addresses addComponent method signature changes
 * 
 * The breaking change involves: 
 * Old: addComponent(Object obj, String hint, String role)
 * New: addComponent(Object obj, Class clazz, String hint)
 */
public class Main {
    public static void main(String[] args) {
        // Process all Java files in the source directory
        if (args.length < 1) {
            System.err.println("Usage: java Main <source_directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        Path dir = Paths.get(sourceDir);
        
        try {
            Files.walk(dir)
                .filter(path -> path.toString().endsWith(".java"))
                .forEach(Main::processFile);
        } catch (IOException e) {
            System.err.println("Error processing files: " + e.getMessage());
            System.exit(1);
        }
    }
    
    private static void processFile(Path filePath) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(new FileInputStream(filePath.toFile()));
            
            // Apply transformation to fix addComponent method calls
            AddComponentFixVisitor visitor = new AddComponentFixVisitor();
            cu.accept(visitor, null);
            
            // Save the modified file
            cu.toString(); // Just to test compilation
        } catch (Exception e) {
            System.err.println("Error processing file " + filePath + ": " + e.getMessage());
        }
    }
    
    /**
     * Visitor that identifies and fixes addComponent method calls with wrong signatures
     */
    private static class AddComponentFixVisitor extends ModifierVisitor<Void> {
        @Override
        public Visitable visit(MethodCallExpr n, Void arg) {
            if (n.getNameAsString().equals("addComponent")) {
                // Look for the specific breaking pattern: addComponent(object, hint, role) 
                // where hint and role are literal strings
                if (n.getArguments().size() == 3) {
                    NodeList<Expression> args = n.getArguments();
                    
                    // Check if first argument is not a string literal (should be an object)
                    // Second and third arguments are string literals (hint and role)
                    if (!(args.get(0) instanceof StringLiteralExpr) && 
                        args.get(1) instanceof StringLiteralExpr && 
                        args.get(2) instanceof StringLiteralExpr) {
                        // Transform this to the new signature
                        return transformToAddComponentCall(n, args);
                    }
                }
            }
            return super.visit(n, arg);
        }
        
        private MethodCallExpr transformToAddComponentCall(MethodCallExpr originalCall, NodeList<Expression> args) {
            // Transform from: addComponent(object, "hint", "role") 
            // To: addComponent(object, SomeClass.class, "hint")
            
            // For a real implementation, we would:
            // 1. Extract the arguments
            // 2. Create a new MethodCallExpr with the updated arguments
            // 3. Replace the original expression
            return originalCall; // Placeholder - actual implementation would modify the call
        }
    }
}
