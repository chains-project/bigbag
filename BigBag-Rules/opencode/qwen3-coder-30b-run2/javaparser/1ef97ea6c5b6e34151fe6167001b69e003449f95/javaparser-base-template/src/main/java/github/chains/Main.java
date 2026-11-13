package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Generic JavaParser transformation rule for fixing timestamp conversion issues
 * caused by breaking changes in datafaker dependencies.
 * This transformation targets code patterns where Date objects are wrapped in Timestamp constructors.
 */
public class Main {
    public static void main(String[] args) throws IOException {
        // Process all Java files in the input directory recursively
        String inputDir = args.length > 0 ? args[0] : "/workspace/flink-faker";
        
        Files.walk(Paths.get(inputDir))
            .filter(path -> path.toString().endsWith(".java"))
            .forEach(Main::processFile);
    }
    
    private static void processFile(Path path) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(new FileInputStream(path.toFile()));
            
            // Apply the transformation to fix timestamp conversion patterns
            new TimestampPatternFixer().visit(cu, null);
            
            // Save the modified file
            cu.save(path.toFile());
        } catch (Exception e) {
            System.err.println("Error processing " + path + ": " + e.getMessage());
        }
    }
    
    /**
     * Visitor to fix timestamp conversion patterns that break due to API changes
     */
    private static class TimestampPatternFixer extends VoidVisitorAdapter<Void> {
        @Override
        public void visit(MethodDeclaration md, Void arg) {
            // Look for methods that return java.sql.Timestamp and create it from Date objects
            if (md.getType().asString().equals("java.sql.Timestamp")) {
                // Check if method body has construction pattern: new Timestamp(date.getTime())
                md.getBody().ifPresent(body -> {
                    body.getStatements().forEach(stmt -> {
                        if (stmt instanceof ReturnStmt) {
                            ReturnStmt returnStmt = (ReturnStmt) stmt;
                            returnStmt.getExpression().ifPresent(expr -> {
                                // Handle: return new Timestamp(someDate.getTime())
                                if (expr instanceof ObjectCreationExpr) {
                                    ObjectCreationExpr creation = (ObjectCreationExpr) expr;
                                    if (creation.getType().getNameAsString().equals("Timestamp")) {
                                        // Extract the original Date expression
                                        NodeList<Expression> args = creation.getArguments();
                                        if (args.size() == 1) {
                                            Expression argExpr = args.get(0);
                                            if (argExpr instanceof MethodCallExpr) {
                                                MethodCallExpr methodCall = (MethodCallExpr) argExpr;
                                                if (methodCall.getNameAsString().equals("getTime")) {
                                                    // Replace with direct return of the date object
                                                    Expression dateExpr = methodCall.getScope().orElse(null);
                                                    if (dateExpr != null) {
                                                        returnStmt.setExpression(dateExpr);
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            });
                        }
                    });
                });
            }
            super.visit(md, arg);
        }
    }
}