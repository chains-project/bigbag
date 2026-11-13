package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

public class Main {
    public static void main(String[] args) {
        try {
            // Process all Java files in the source directory
            String sourceDir = args.length > 0 ? args[0] : "/workspace/nem";
            
            // Find all Java files recursively
            Files.walk(Paths.get(sourceDir))
                .filter(path -> path.toString().endsWith(".java"))
                .forEach(Main::processJavaFile);
                
        } catch (Exception e) {
            System.err.println("Error processing files: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private static void processJavaFile(java.nio.file.Path filePath) {
        try {
            String content = Files.readString(filePath);
            CompilationUnit cu = StaticJavaParser.parse(content);
            
            // Apply transformation
            boolean modified = applyTransformation(cu);
            
            if (modified) {
                String result = LexicalPreservingPrinter.print(cu);
                Files.writeString(filePath, result);
                System.out.println("Processed: " + filePath);
            }
        } catch (Exception e) {
            System.err.println("Error processing " + filePath + ": " + e.getMessage());
        }
    }
    
    private static boolean applyTransformation(CompilationUnit cu) {
        boolean modified = false;
        
        // Find all method declarations
        List<MethodDeclaration> methods = cu.findAll(MethodDeclaration.class);
        
        for (MethodDeclaration method : methods) {
            // Find all statements in method body
            if (method.getBody().isPresent()) {
                List<ExpressionStmt> expressionStatements = method.getBody().get().findAll(ExpressionStmt.class);
                
                for (ExpressionStmt stmt : expressionStatements) {
                    // Look for new Flyway() calls
                    if (stmt.getExpression() instanceof ObjectCreationExpr) {
                        ObjectCreationExpr creation = (ObjectCreationExpr) stmt.getExpression();
                        
                        // Check if this is creating a Flyway instance
                        if (creation.getType().asString().equals("Flyway")) {
                            modified = true;
                            
                            // Create the new expression: new Flyway(FluentConfiguration.configure().build())
                            ObjectCreationExpr newCreation = new ObjectCreationExpr();
                            newCreation.setType(creation.getType());
                            
                            // Build the constructor arguments: FluentConfiguration.configure().build()
                            MethodCallExpr fluentConfigure = new MethodCallExpr(
                                new NameExpr("org.flywaydb.core.api.configuration.FluentConfiguration"),
                                "configure"
                            );
                            MethodCallExpr buildCall = new MethodCallExpr(fluentConfigure, "build");
                            
                            newCreation.setArguments(List.of(buildCall));
                            
                            // Replace the statement with the new creation
                            stmt.setExpression(newCreation);
                        }
                    }
                }
            }
        }
        
        return modified;
    }
}
