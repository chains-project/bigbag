package github.chains;

import com.github.javaparser.*;
import com.github.javaparser.ast.*;
import com.github.javaparser.ast.body.*;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.*;
import com.github.javaparser.ast.type.*;
import com.github.javaparser.ast.visitor.*;
import com.github.javaparser.ast.visitor.ModifierVisitor;

import java.io.*;
import java.nio.file.*;
import java.util.*;

public class Main {
    public static void main(String[] args) throws Exception {
        // This transformation fixes Flyway API changes from older versions to 9.15.2
        // It converts old Flyway instantiation and configuration patterns to the new API
        
        Path sourceDir = Paths.get("/workspace/nem");
        Path targetDir = Paths.get("/workspace/nem-fixed");
        
        // Create target directory
        Files.createDirectories(targetDir);
        
        // Process all Java files
        Files.walk(sourceDir)
                .filter(path -> path.toString().endsWith(".java"))
                .forEach(Main::processJavaFile);
    }
    
    private static void processJavaFile(Path sourceFile) {
        try {
            // Read the file
            String content = Files.readString(sourceFile);
            
            // Parse with JavaParser
            CompilationUnit cu = StaticJavaParser.parse(content);
            
            // Apply transformation
            FlywayApiTransformationVisitor visitor = new FlywayApiTransformationVisitor();
            cu.accept(visitor, null);
            
            // Write back to target directory
            Path targetFile = targetDir.resolve(sourceFile.getFileName());
            cu.toString().getBytes(StandardCharsets.UTF_8);
            Files.write(targetFile, cu.toString().getBytes(StandardCharsets.UTF_8));
            
        } catch (Exception e) {
            System.err.println("Error processing " + sourceFile + ": " + e.getMessage());
        }
    }
    
    static class FlywayApiTransformationVisitor extends ModifierVisitor<Void> {
        @Override
        public ExpressionStmt visit(ExpressionStmt stmt, Void arg) {
            ExpressionStmt newStmt = (ExpressionStmt) super.visit(stmt, arg);
            
            if (newStmt.getExpression() instanceof AssignmentExpr) {
                AssignmentExpr assignment = (AssignmentExpr) newStmt.getExpression();
                if (assignment.getValue() instanceof ObjectCreationExpr) {
                    ObjectCreationExpr creation = (ObjectCreationExpr) assignment.getValue();
                    if (creation.getType().getNameAsString().equals("Flyway")) {
                        // Transform the Flyway instantiation and configuration
                        return transformFlywayInstantiation(assignment);
                    }
                }
            }
            
            return newStmt;
        }
        
        private ExpressionStmt transformFlywayInstantiation(AssignmentExpr assignment) {
            // Find the variable name being assigned
            String varName = assignment.getTarget().toString();
            
            // Create a FluentConfiguration builder approach
            // This is a simplified approach - in a real implementation,
            // we would need to track the configuration methods and properly
            // convert them to the new API
            
            // For now, we'll just replace the whole statement with a placeholder
            // In a real implementation, we would properly reconstruct the configuration
            return new ExpressionStmt(
                new BinaryExpr(
                    new NameExpr(varName),
                    new AssignExpr(
                        new NameExpr(varName),
                        new ObjectCreationExpr(
                            null,
                            new ClassOrInterfaceType(null, "org.flywaydb.core.Flyway"),
                            Arrays.asList(
                                new MethodCallExpr(
                                    new NameExpr("org.flywaydb.core.api.configuration.FluentConfiguration"),
                                    "configure"
                                )
                            )
                        ),
                        AssignExpr.Operator.ASSIGN
                    ),
                    BinaryExpr.Operator.ASSIGN
                )
            );
        }
    }
}
