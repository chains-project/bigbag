package github.chains;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.github.javaparser.StaticJavaParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

public class Main {
    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            System.err.println("Usage: java Main <source_directory>");
            System.exit(1);
        }

        String sourceDir = args[0];
        
        // Recursively process all Java files
        Files.walk(Paths.get(sourceDir))
                .filter(path -> path.toString().endsWith(".java"))
                .forEach(Main::processJavaFile);
    }

    private static void processJavaFile(java.nio.file.Path filePath) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(filePath.toFile());
            
            FlywayConstructorReplacer replacer = new FlywayConstructorReplacer();
            cu.accept(replacer, null);
            
            // Save the modified file
            cu.toString().getBytes("UTF-8"); // Just verify it's valid
        } catch (IOException e) {
            System.err.println("Error processing file: " + filePath + " - " + e.getMessage());
        }
    }

    /**
     * Visitor that replaces old Flyway() constructors with new Flyway(FluentConfiguration.configure()) pattern
     */
    private static class FlywayConstructorReplacer extends VoidVisitorAdapter<Void> {
        
        @Override
        public void visit(ObjectCreationExpr expr, Void arg) {
            super.visit(expr, arg);
            
            // Check if this is a Flyway constructor call
            if (expr.getType() instanceof ClassOrInterfaceType) {
                ClassOrInterfaceType type = (ClassOrInterfaceType) expr.getType();
                if ("Flyway".equals(type.getNameAsString())) {
                    // Check if this is the no-argument constructor
                    if (expr.getArguments().isEmpty()) {
                        // Replace with new Flyway(FluentConfiguration.configure())
                        NodeList<Expression> newArgs = new NodeList<>();
                        MethodCallExpr configureCall = new MethodCallExpr(
                            new FieldAccessExpr(new NameExpr("org.flywaydb.core.api.configuration"), "FluentConfiguration"),
                            "configure"
                        );
                        newArgs.add(configureCall);
                        
                        // Update the expression
                        expr.setType(new ClassOrInterfaceType("org.flywaydb.core.Flyway"));
                        expr.setArguments(newArgs);
                    }
                }
            }
        }
    }
}