package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.utils.SourceRoot;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Main {
    public static void main(String[] args) throws IOException {
        // This is a generic transformation for Flyway constructor changes
        // It replaces new Flyway() with new Flyway(Configuration)
        String sourceDir = "/workspace/nem"; // Change this to your project path
        
        if (args.length > 0) {
            sourceDir = args[0];
        }
        
        // Process all Java files
        Files.walk(Paths.get(sourceDir))
                .filter(path -> path.toString().endsWith(".java"))
                .forEach(Main::processJavaFile);
    }

    private static void processJavaFile(Path path) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(path.toFile());
            
            // Apply transformation to fix Flyway constructor calls
            FlywayConstructorFixVisitor visitor = new FlywayConstructorFixVisitor();
            cu.accept(visitor, null);
            
            // Save the modified file
            SourceRoot sourceRoot = new SourceRoot(path.getParent());
            sourceRoot.add(cu);
            sourceRoot.saveAll();
        } catch (Exception e) {
            System.err.println("Error processing " + path + ": " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    static class FlywayConstructorFixVisitor extends ModifierVisitor<Void> {
        @Override
        public Visitable visit(ObjectCreationExpr expr, Void arg) {
            // Check if it's a Flyway constructor call
            if ("Flyway".equals(expr.getType().getNameAsString())) {
                // Check if it's a no-argument constructor
                if (expr.getArguments().isEmpty()) {
                    // Replace with new Flyway(Configuration) call
                    NodeList<Expression> arguments = new NodeList<>();
                    
                    // Create the call to FluentConfiguration.configure()
                    MethodCallExpr configureCall = new MethodCallExpr(
                        new NameExpr("org.flywaydb.core.api.configuration.FluentConfiguration"),
                        "configure"
                    );
                    
                    // Create new Flyway(configureCall) 
                    ObjectCreationExpr newFlyway = new ObjectCreationExpr();
                    newFlyway.setType(new ClassOrInterfaceType("org.flywaydb.core.Flyway"));
                    newFlyway.setArguments(arguments);
                    arguments.add(configureCall);
                    
                    return newFlyway;
                }
            }
            return super.visit(expr, arg);
        }
    }
}
