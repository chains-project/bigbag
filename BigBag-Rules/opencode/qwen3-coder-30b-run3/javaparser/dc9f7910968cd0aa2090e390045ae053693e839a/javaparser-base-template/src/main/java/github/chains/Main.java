package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.Expression;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Main {
    public static void main(String[] args) {
        try {
            Path projectDir = Paths.get("/workspace/nem");
            processDirectory(projectDir);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void processDirectory(Path dir) throws IOException {
        Files.walk(dir)
                .filter(path -> path.toString().endsWith(".java"))
                .forEach(Main::processJavaFile);
    }

    private static void processJavaFile(Path filePath) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(filePath.toFile());
            
            // Apply transformation to fix Flyway constructor calls
            FlywayConstructorFixer visitor = new FlywayConstructorFixer();
            visitor.visit(cu, null);
            
            // Write the updated content back to file
            try (FileWriter writer = new FileWriter(filePath.toFile())) {
                writer.write(cu.toString());
            }
        } catch (Exception e) {
            System.err.println("Error processing file " + filePath + ": " + e.getMessage());
        }
    }

    /**
     * Visitor to find and fix Flyway constructor calls that use new Flyway() without arguments
     */
    private static class FlywayConstructorFixer extends VoidVisitorAdapter<Void> {
        @Override
        public void visit(ObjectCreationExpr n, Void arg) {
            super.visit(n, arg);
            
            // Check if this is a new Flyway() call
            if (n.getType().getNameAsString().equals("Flyway")) {
                // Check if it's a parameterless constructor call
                if (n.getArguments().isEmpty()) {
                    // Replace with Flyway.configure().load() pattern
                    Expression newExpr = new MethodCallExpr(
                        new MethodCallExpr(new NameExpr("Flyway"), "configure"),
                        "load"
                    );
                    
                    // Find the parent statement to replace it
                    Statement parentStmt = n.findAncestor(Statement.class).orElse(null);
                    if (parentStmt != null) {
                        // Replace the entire statement with the new pattern
                        if (parentStmt instanceof ExpressionStmt) {
                            ExpressionStmt exprStmt = (ExpressionStmt) parentStmt;
                            exprStmt.setExpression(newExpr);
                        }
                    }
                }
            }
        }
    }
}