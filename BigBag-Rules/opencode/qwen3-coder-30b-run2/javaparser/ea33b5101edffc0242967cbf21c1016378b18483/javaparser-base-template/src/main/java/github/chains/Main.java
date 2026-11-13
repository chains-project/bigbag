package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Generic transformation rule to fix Flyway API breaking changes from version 6.x to 9.15.2
 * 
 * This transformation replaces:
 * 1. Old pattern: new Flyway(); flyway.setDataSource(...); flyway.setLocations(...);
 *    with new pattern: Flyway.configure().dataSource(...).locations(...).load()
 *    
 * 2. Old pattern: new Flyway(); flyway.setDataSource(...); flyway.setClassLoader(...); flyway.setLocations(...); flyway.setValidateOnMigrate(...);
 *    with new pattern: Flyway.configure().dataSource(...).classLoader(...).locations(...).validateOnMigrate(...).load() 
 */
public class Main {
    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Usage: java Main <source_directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        try {
            transformDirectory(sourceDir);
        } catch (IOException e) {
            System.err.println("Error processing files: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private static void transformDirectory(String sourceDir) throws IOException {
        Path dirPath = Paths.get(sourceDir);
        Files.walk(dirPath)
                .filter(path -> path.toString().endsWith(".java"))
                .forEach(Main::transformFile);
    }
    
    private static void transformFile(Path filePath) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(filePath.toFile());
            
            // Apply the transformation
            FlywayTransformVisitor visitor = new FlywayTransformVisitor();
            visitor.visit(cu, null);
            
            // Save the transformed file
            cu.save(filePath.toFile());
        } catch (Exception e) {
            System.err.println("Error transforming " + filePath + ": " + e.getMessage());
        }
    }
    
    /**
     * Visitor that identifies and transforms old Flyway API usage
     */
    private static class FlywayTransformVisitor extends VoidVisitorAdapter<Void> {
        @Override
        public void visit(MethodDeclaration md, Void arg) {
            super.visit(md, arg);
            // Find all statements in method body that might contain flyway usage
            NodeList<Statement> statements = md.getBody().orElse(null).getStatements();
            if (statements == null) return;
            
            // Store the indices of statements that need to be removed
            NodeList<Statement> statementsToRemove = new NodeList<>();
            
            // Process each statement to detect old Flyway API usage
            for (int i = 0; i < statements.size(); i++) {
                Statement stmt = statements.get(i);
                
                // Check for patterns like: Flyway flyway = new Flyway();
                if (stmt.isExpressionStmt()) {
                    ExpressionStmt exprStmt = stmt.asExpressionStmt();
                    Expression expression = exprStmt.getExpression();
                    
                    // Look for assignment expression with new Flyway()
                    if (expression.isAssignExpr()) {
                        AssignExpr assignExpr = expression.asAssignExpr();
                        Expression value = assignExpr.getValue();
                        
                        if (value.isObjectCreationExpr()) {
                            ObjectCreationExpr objectCreation = value.asObjectCreationExpr();
                            if (objectCreation.getType().getNameAsString().equals("Flyway")) {
                                // Check if it's a constructor with no arguments
                                if (objectCreation.getArguments().isEmpty()) {
                                    // Found the pattern: Flyway flyway = new Flyway();
                                    // This needs to be replaced with configure() pattern
                                    transformFlywayInitialization(assignExpr, statements, statementsToRemove, i);
                                }
                            }
                        }
                    }
                }
            }
            
            // Remove statements in reverse order to maintain correct indices
            for (int i = statementsToRemove.size() - 1; i >= 0; i--) {
                statements.remove(statementsToRemove.get(i));
            }
        }
        
        /**
         * Transforms the old Flyway initialization to use the new FluentConfiguration approach
         */
        private void transformFlywayInitialization(AssignExpr assignExpr, NodeList<Statement> statements, NodeList<Statement> statementsToRemove, int assignIndex) {
            // Extract the variable name
            String varName = "";
            if (assignExpr.getTarget().isNameExpr()) {
                varName = assignExpr.getTarget().asNameExpr().getNameAsString();
            } else {
                // If we can't extract variable name, skip transformations
                return;
            }
            
            // Build the replacement expression using fluent API
            // Start with: Flyway.configure()
            MethodCallExpr fluentCall = new MethodCallExpr(new NameExpr("Flyway"), "configure");
            
            // Find all subsequent statements that are method calls on this variable
            int j = assignIndex + 1;
            boolean hasSetters = false;
            
            while (j < statements.size()) {
                Statement stmt = statements.get(j);
                if (!stmt.isExpressionStmt()) {
                    break;
                }
                
                ExpressionStmt exprStmt = stmt.asExpressionStmt();
                Expression expression = exprStmt.getExpression();
                
                if (expression.isMethodCallExpr()) {
                    MethodCallExpr methodCall = expression.asMethodCallExpr();
                    
                    // Check if this method call is on our flyway variable
                    if (methodCall.getScope().isNameExpr()) {
                        NameExpr scope = methodCall.getScope().asNameExpr();
                        if (scope.getNameAsString().equals(varName)) {
                            String methodName = methodCall.getNameAsString();
                            
                            // Transform setters to fluent calls
                            if ("setDataSource".equals(methodName)) {
                                // Replace with: .dataSource(...)
                                MethodCallExpr newMethodCall = new MethodCallExpr(fluentCall, "dataSource");
                                newMethodCall.addArgument(methodCall.getArguments().get(0));
                                fluentCall = newMethodCall;
                                hasSetters = true;
                            } else if ("setClassLoader".equals(methodName)) {
                                // Replace with: .classLoader(...)
                                MethodCallExpr newMethodCall = new MethodCallExpr(fluentCall, "classLoader");
                                newMethodCall.addArgument(methodCall.getArguments().get(0));
                                fluentCall = newMethodCall;
                                hasSetters = true;
                            } else if ("setLocations".equals(methodName)) {
                                // Replace with: .locations(...)
                                MethodCallExpr newMethodCall = new MethodCallExpr(fluentCall, "locations");
                                newMethodCall.addArgument(methodCall.getArguments().get(0));
                                fluentCall = newMethodCall;
                                hasSetters = true;
                            } else if ("setValidateOnMigrate".equals(methodName)) {
                                // Replace with: .validateOnMigrate(...)
                                MethodCallExpr newMethodCall = new MethodCallExpr(fluentCall, "validateOnMigrate");
                                newMethodCall.addArgument(methodCall.getArguments().get(0));
                                fluentCall = newMethodCall;
                                hasSetters = true;
                            } else {
                                // Unknown setter, continue without modifying
                                j++;
                                continue;
                            }
                            
                            // Mark this statement for removal
                            statementsToRemove.add(stmt);
                            j++;
                        } else {
                            // Not our variable, stop looking
                            break;
                        }
                    } else {
                        // Not a method call on variable, stop looking
                        break;
                    }
                } else {
                    // Not a method call expression, stop looking
                    break;
                }
            }
            
            // Add the .load() call to complete the fluent chain if there were setters
            if (hasSetters) {
                MethodCallExpr loadCall = new MethodCallExpr(fluentCall, "load");
                assignExpr.setValue(loadCall);
            } else {
                // No setters, just replace with the simplest configure pattern
                MethodCallExpr simpleCall = new MethodCallExpr(new NameExpr("Flyway"), "configure");
                MethodCallExpr loadCall = new MethodCallExpr(simpleCall, "load");
                assignExpr.setValue(loadCall);
            }
        }
    }
}
