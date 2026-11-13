package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Main {
    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Usage: java Main <source_directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        Path dir = Paths.get(sourceDir);
        
        // Process all Java files in the directory recursively
        try {
            Files.walk(dir)
                    .filter(path -> path.toString().endsWith(".java"))
                    .forEach(Main::processJavaFile);
        } catch (IOException e) {
            System.err.println("Error walking directory: " + e.getMessage());
        }
    }
    
    private static void processJavaFile(Path filePath) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(filePath.toFile());
            
            // Apply the Flyway migration visitor
            FlywayMigrationVisitor visitor = new FlywayMigrationVisitor();
            cu.accept(visitor, null);
            
            // Just print that we processed the file
            System.out.println("Processed: " + filePath);
        } catch (IOException e) {
            System.err.println("Error processing file " + filePath + ": " + e.getMessage());
        }
    }
    
    private static class FlywayMigrationVisitor extends ModifierVisitor<Void> {
        @Override
        public Visitable visit(ObjectCreationExpr n, Void arg) {
            // Check if this is a Flyway constructor call with no arguments (the old pattern)
            if (n.getType().getNameAsString().equals("Flyway") && n.getArguments().isEmpty()) {
                // Replace with new fluent configuration pattern - this is the main fix!
                // Replace: new Flyway()
                // With: org.flywaydb.core.Flyway.configure().load()
                
                NameExpr configureExpr = new NameExpr("org.flywaydb.core.Flyway.configure");
                MethodCallExpr configureCall = new MethodCallExpr(configureExpr, "configure");
                MethodCallExpr loadCall = new MethodCallExpr(configureCall, "load");
                
                return loadCall;
            }
            
            return super.visit(n, arg);
        }
    }
}