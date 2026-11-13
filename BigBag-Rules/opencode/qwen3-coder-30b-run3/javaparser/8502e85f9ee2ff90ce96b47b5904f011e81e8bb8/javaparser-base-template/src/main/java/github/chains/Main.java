package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.Node;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;

public class Main {
    public static void main(String[] args) {
        // This is a generic transformation to fix Flyway constructor breaking change
        // from version 9.20.1 where Flyway() constructor now requires Configuration parameter
        
        if (args.length < 1) {
            System.err.println("Usage: java Main <path_to_source_directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        try {
            processDirectory(sourceDir);
        } catch (IOException e) {
            System.err.println("Error processing files: " + e.getMessage());
            System.exit(1);
        }
    }
    
    private static void processDirectory(String directoryPath) throws IOException {
        Path dir = Paths.get(directoryPath);
        if (!Files.exists(dir)) {
            throw new IOException("Directory does not exist: " + directoryPath);
        }
        
        Files.walk(dir)
                .filter(path -> path.toString().endsWith(".java"))
                .forEach(Main::processJavaFile);
    }
    
    private static void processJavaFile(Path filePath) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(filePath.toFile());
            
            // Find all Flyway object creation expressions that match the old pattern
            // i.e., new Flyway() without parameters
            boolean modified = false;
            List<ObjectCreationExpr> flywayCreations = cu.findAll(ObjectCreationExpr.class)
                    .stream()
                    .filter(expr -> expr.getType().getNameAsString().equals("Flyway"))
                    .filter(expr -> expr.getArguments().isEmpty())  // Only parameterless constructors
                    .toList();
            
            for (ObjectCreationExpr flywayCreation : flywayCreations) {
                // Find the statement that contains this object creation
                Optional<Node> parentOpt = flywayCreation.getParentNode();
                if (parentOpt.isPresent()) {
                    Node parentNode = parentOpt.get();
                    
                    // Replace the old pattern with new pattern:
                    // Old: Flyway flyway = new Flyway(); 
                    // New: Flyway flyway = Flyway.configure().load();
                    
                    if (parentNode instanceof ExpressionStmt) {
                        ExpressionStmt exprStmt = (ExpressionStmt) parentNode;
                        Expression expr = exprStmt.getExpression();
                        
                        // Handle assignment expressions (flyway = new Flyway())
                        if (expr instanceof AssignExpr) {
                            AssignExpr assignExpr = (AssignExpr) expr;
                            if (assignExpr.getValue() instanceof ObjectCreationExpr) {
                                ObjectCreationExpr initializer = (ObjectCreationExpr) assignExpr.getValue();
                                if (initializer.getType().getNameAsString().equals("Flyway") &&
                                    initializer.getArguments().isEmpty()) {
                                    // Convert assignment to use configure method
                                    MethodCallExpr configureCall = new MethodCallExpr(
                                        new NameExpr("Flyway"), 
                                        "configure"
                                    );
                                    MethodCallExpr loadCall = new MethodCallExpr(configureCall, "load");
                                    assignExpr.setValue(loadCall);
                                    modified = true;
                                }
                            }
                        }
                    }
                }
            }
            
            // If modified, we would save the file here
            if (modified) {
                System.out.println("Would modify: " + filePath);
            }
        } catch (Exception e) {
            System.err.println("Error processing file " + filePath + ": " + e.getMessage());
            e.printStackTrace();
        }
    }
}