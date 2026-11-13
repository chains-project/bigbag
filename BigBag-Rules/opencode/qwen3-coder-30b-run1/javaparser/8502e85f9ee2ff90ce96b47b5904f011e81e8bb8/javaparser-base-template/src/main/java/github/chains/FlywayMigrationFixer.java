package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.BooleanLiteralExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.expr.NullLiteralExpr;
import com.github.javaparser.ast.expr.CastExpr;
import com.github.javaparser.ast.expr.EnclosedExpr;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.SimpleName;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.NodeList;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class FlywayMigrationFixer {
    public static void main(String[] args) throws IOException {
        // Process all Java files in the project
        Path projectRoot = Paths.get("/workspace/nem");
        Files.walk(projectRoot)
                .filter(path -> path.toString().endsWith(".java"))
                .forEach(FlywayMigrationFixer::processFile);
    }

    private static void processFile(Path filePath) {
        try {
            String content = Files.readString(filePath);
            CompilationUnit cu = StaticJavaParser.parse(content);
            
            // Find and fix Flyway method calls
            boolean modified = false;
            
            // Look for setClassLoader calls
            modified |= fixSetClassLoader(cu);
            
            // Look for setLocations calls
            modified |= fixSetLocations(cu);
            
            // Look for setValidateOnMigrate calls
            modified |= fixSetValidateOnMigrate(cu);
            
            if (modified) {
                // Save the modified file
                Files.writeString(filePath, cu.toString());
                System.out.println("Updated: " + filePath);
            }
        } catch (Exception e) {
            System.err.println("Error processing " + filePath + ": " + e.getMessage());
        }
    }

    private static boolean fixSetClassLoader(CompilationUnit cu) {
        boolean modified = false;
        
        // Find all method calls to setClassLoader
        List<MethodCallExpr> methodCalls = cu.findAll(MethodCallExpr.class).stream()
                .filter(call -> call.getNameAsString().equals("setClassLoader"))
                .collect(Collectors.toList());
        
        for (MethodCallExpr call : methodCalls) {
            // Remove the entire method call statement
            Optional<ExpressionStmt> parentStmt = call.getParentNode().filter(ExpressionStmt.class::isInstance)
                    .map(ExpressionStmt.class::cast);
            
            if (parentStmt.isPresent()) {
                parentStmt.get().remove();
                modified = true;
            }
        }
        
        return modified;
    }

    private static boolean fixSetLocations(CompilationUnit cu) {
        boolean modified = false;
        
        // Find all method calls to setLocations
        List<MethodCallExpr> methodCalls = cu.findAll(MethodCallExpr.class).stream()
                .filter(call -> call.getNameAsString().equals("setLocations"))
                .collect(Collectors.toList());
        
        for (MethodCallExpr call : methodCalls) {
            // Remove the entire method call statement
            Optional<ExpressionStmt> parentStmt = call.getParentNode().filter(ExpressionStmt.class::isInstance)
                    .map(ExpressionStmt.class::cast);
            
            if (parentStmt.isPresent()) {
                parentStmt.get().remove();
                modified = true;
            }
        }
        
        return modified;
    }

    private static boolean fixSetValidateOnMigrate(CompilationUnit cu) {
        boolean modified = false;
        
        // Find all method calls to setValidateOnMigrate
        List<MethodCallExpr> methodCalls = cu.findAll(MethodCallExpr.class).stream()
                .filter(call -> call.getNameAsString().equals("setValidateOnMigrate"))
                .collect(Collectors.toList());
        
        for (MethodCallExpr call : methodCalls) {
            // Remove the entire method call statement
            Optional<ExpressionStmt> parentStmt = call.getParentNode().filter(ExpressionStmt.class::isInstance)
                    .map(ExpressionStmt.class::cast);
            
            if (parentStmt.isPresent()) {
                parentStmt.get().remove();
                modified = true;
            }
        }
        
        return modified;
    }
}