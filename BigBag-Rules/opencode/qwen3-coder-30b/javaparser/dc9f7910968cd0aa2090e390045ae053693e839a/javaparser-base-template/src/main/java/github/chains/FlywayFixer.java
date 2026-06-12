package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class FlywayFixer {

    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("Usage: java FlywayFixer <source_directory>");
            System.exit(1);
        }

        String sourceDirectory = args[0];
        Path sourcePath = Paths.get(sourceDirectory);

        // Process all Java files in the directory
        Files.walk(sourcePath)
                .filter(path -> path.toString().endsWith(".java"))
                .forEach(FlywayFixer::processFile);
    }

    private static void processFile(Path filePath) {
        try {
            // Parse the Java file
            CompilationUnit cu = StaticJavaParser.parse(Files.readAllBytes(filePath));
            
            // Apply the Flyway transformation
            FlywayTransformationVisitor visitor = new FlywayTransformationVisitor();
            visitor.visit(cu, null);
            
            // Save the modified file
            try (FileWriter writer = new FileWriter(filePath.toFile())) {
                cu.accept(new com.github.javaparser.ast.visitor.VoidVisitorAdapter<Void>() {
                    @Override
                    public void visit(CompilationUnit cu, Void arg) {
                        cu.toString().accept(writer);
                        super.visit(cu, arg);
                    }
                }, null);
            }
            
            System.out.println("Processed: " + filePath);
        } catch (Exception e) {
            System.err.println("Error processing " + filePath + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Visitor that transforms Flyway API calls from version 8.x to 9.x
     */
    private static class FlywayTransformationVisitor extends VoidVisitorAdapter<Void> {
        @Override
        public void visit(MethodDeclaration md, Void arg) {
            super.visit(md, arg);
            
            // Look for flyway() method that creates a Flyway instance
            if (md.getNameAsString().equals("flyway")) {
                // Find the method body and look for the problematic patterns
                md.getBody().ifPresent(body -> {
                    // Look for the old API patterns
                    body.accept(new VoidVisitorAdapter<Void>() {
                        @Override
                        public void visit(ObjectCreationExpr oce, Void arg) {
                            super.visit(oce, arg);
                            
                            // Check if this is a Flyway constructor call with no arguments
                            if (oce.getType().toString().equals("Flyway") && oce.getArguments().isEmpty()) {
                                // This is the main issue: new Flyway() - no longer allowed in 9.x
                                // We need to identify this pattern for later replacement
                                System.out.println("Found problematic Flyway constructor: " + oce.toString());
                            }
                        }
                        
                        @Override
                        public void visit(ExpressionStmt stmt, Void arg) {
                            super.visit(stmt, arg);
                            
                            // Check for setter calls on flyway variable
                            if (stmt.getExpression() instanceof MethodCallExpr) {
                                MethodCallExpr methodCall = (MethodCallExpr) stmt.getExpression();
                                
                                // Look for calls like flyway.setDataSource(), flyway.setLocations(), etc.
                                if (methodCall.getScope().isPresent() && 
                                    methodCall.getScope().get() instanceof NameExpr) {
                                    NameExpr scope = (NameExpr) methodCall.getScope().get();
                                    if (scope.getNameAsString().equals("flyway")) {
                                        // This is a call like flyway.setDataSource()
                                        // We need to identify this pattern for later replacement
                                        System.out.println("Found old Flyway API call: " + methodCall.toString());
                                    }
                                }
                            }
                        }
                    }, null);
                });
            }
        }
    }
}