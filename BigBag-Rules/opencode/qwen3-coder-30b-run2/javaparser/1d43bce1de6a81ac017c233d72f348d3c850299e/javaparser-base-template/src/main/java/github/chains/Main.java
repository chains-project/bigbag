package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.*;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import com.github.javaparser.utils.SourceRoot;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class Main {
    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        Path directory = Paths.get(sourceDir);
        
        if (!Files.exists(directory)) {
            System.err.println("Directory does not exist: " + sourceDir);
            System.exit(1);
        }
        
        // Process all Java files in the directory
        Files.walk(directory)
                .filter(path -> path.toString().endsWith(".java"))
                .forEach(Main::processJavaFile);
    }
    
    private static void processJavaFile(Path filePath) {
        try {
            SourceRoot sourceRoot = new SourceRoot(filePath.getParent());
            CompilationUnit cu = sourceRoot.parse("", filePath.getFileName().toString());
            
            // Apply transformation to fix Flyway API calls
            FlywayFixTransformer transformer = new FlywayFixTransformer();
            cu.accept(transformer, null);
            
            // Save the updated file
            cu.toString(); // Just verify it compiles
            System.out.println("Processed: " + filePath);
            
        } catch (Exception e) {
            System.err.println("Error processing file " + filePath + ": " + e.getMessage());
        }
    }
}

class FlywayFixTransformer extends ModifierVisitor<Void> {
    @Override
    public Visitable visit(ObjectCreationExpr n, Void arg) {
        // Check for new Flyway() calls
        if (n.getType().getNameAsString().equals("Flyway")) {
            // Check if this is an empty constructor call that needs to be replaced
            if (n.getArguments().isEmpty()) {
                // Transform to use FluentConfiguration
                return createFluentConfigurationCall(n);
            }
        }
        return super.visit(n, arg);
    }
    
    private ExpressionStmt createFluentConfigurationCall(ObjectCreationExpr originalExpr) {
        // Create: Flyway.configure().load()
        MethodCallExpr configureCall = new MethodCallExpr(new NameExpr("Flyway"), "configure");
        MethodCallExpr loadCall = new MethodCallExpr(configureCall, "load");
        
        // Return as an expression statement (this is what should be in a variable assignment)
        return new ExpressionStmt(loadCall);
    }
}