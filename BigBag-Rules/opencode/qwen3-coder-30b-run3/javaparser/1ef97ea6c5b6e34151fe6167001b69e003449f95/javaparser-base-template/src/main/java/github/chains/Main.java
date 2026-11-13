package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Main {

    public static void main(String[] args) throws Exception {
        // Process all Java files in the flink-faker source directory
        String sourcePath = "/workspace/flink-faker/src/main/java/";
        String targetPath = "/workspace/flink-faker/src/main/java/";

        Files.walk(Paths.get(sourcePath))
                .filter(path -> path.toString().endsWith(".java"))
                .forEach(path -> {
                    try {
                        processFile(path.toString(), targetPath);
                    } catch (Exception e) {
                        System.err.println("Error processing " + path + ": " + e.getMessage());
                        e.printStackTrace();
                    }
                });
    }

    private static void processFile(String sourceFilePath, String targetPath) throws Exception {
        // Parse the Java file
        CompilationUnit cu = StaticJavaParser.parse(new File(sourceFilePath));
        
        // Apply transformation
        cu.accept(new TimestampReturnFixer(), null);
        
        // Write back the modified content
        String modifiedContent = cu.toString();
        Path outputPath = Paths.get(targetPath).resolve(sourceFilePath.substring(sourcePath.length()));
        Files.createDirectories(outputPath.getParent());
        Files.write(outputPath, modifiedContent.getBytes());
    }

    /**
     * Visitor that specifically targets the DateAndTime breaking change pattern:
     * Changes return type from Date to Timestamp in super.past(), super.future(), super.between(), super.birthday()
     * from the pattern: return new Timestamp(super.method().getTime()); 
     * to: return super.method();
     */
    private static class TimestampReturnFixer extends ModifierVisitor<Void> {
        @Override
        public Visitable visit(ReturnStmt n, Void arg) {
            // Look for the specific pattern: return new Timestamp(super.method().getTime());
            if (n.getExpression().isPresent()) {
                Expression expr = n.getExpression().get();
                
                // Pattern matching for: new Timestamp(...) construction
                if (expr instanceof ObjectCreationExpr) {
                    ObjectCreationExpr creation = (ObjectCreationExpr) expr;
                    
                    // Check if it's creating a Timestamp object
                    if (creation.getType().getNameAsString().equals("Timestamp")) {
                        
                        // Check if there's exactly one argument
                        if (creation.getArguments().size() == 1) {
                            Expression argExpr = creation.getArguments().get(0);
                            
                            // Look for the pattern: super.method().getTime()  
                            if (argExpr instanceof MethodCallExpr) {
                                MethodCallExpr methodCall = (MethodCallExpr) argExpr;
                                
                                // Check if it's the .getTime() method call
                                if (methodCall.getNameAsString().equals("getTime") && 
                                    methodCall.getScope().isPresent()) {
                                    
                                    // The scope should be: super.method()
                                    Expression scopeExpr = methodCall.getScope().get();
                                    if (scopeExpr instanceof MethodCallExpr) {
                                        MethodCallExpr innerMethodCall = (MethodCallExpr) scopeExpr;
                                        
                                        // Check if it's a super call
                                        if (innerMethodCall.getScope().isPresent() &&
                                            innerMethodCall.getScope().get() instanceof NameExpr) {
                                            
                                            NameExpr nameExpr = (NameExpr) innerMethodCall.getScope().get();
                                            if (nameExpr.getNameAsString().equals("super")) {
                                                
                                                // This is the problematic pattern we want to fix:
                                                // return new Timestamp(super.past(...).getTime());
                                                // Change to:
                                                // return super.past(...);
                                                return new ReturnStmt(innerMethodCall);
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            return super.visit(n, arg);
        }
    }
}