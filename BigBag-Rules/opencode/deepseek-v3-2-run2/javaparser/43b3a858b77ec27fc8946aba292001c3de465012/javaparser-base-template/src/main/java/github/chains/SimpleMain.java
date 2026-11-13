package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.CastExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.type.ClassOrInterfaceType;

import java.io.FileInputStream;
import java.io.FileOutputStream;

public class SimpleMain {
    public static void main(String[] args) throws Exception {
        JavaParser parser = new JavaParser();
        CompilationUnit cu = parser.parse(new FileInputStream("../pdb/src/test/java/com/feedzai/commons/sql/abstraction/engine/impl/abs/EngineCloseTest.java")).getResult().orElse(null);
        
        if (cu == null) {
            System.out.println("Failed to parse file");
            return;
        }
        
        // Use an array to work around lambda variable capture limitation
        final boolean[] wasModified = {false};
        
        // Find all cast expressions that cast LoggerFactory.getLogger() to Logger
        cu.findAll(CastExpr.class).forEach(castExpr -> {
            // Check if this is a cast to Logger
            if (castExpr.getType() instanceof ClassOrInterfaceType) {
                ClassOrInterfaceType type = (ClassOrInterfaceType) castExpr.getType();
                
                if (type.getNameAsString().equals("Logger")) {
                    // Check if the expression being cast is LoggerFactory.getLogger()
                    if (castExpr.getExpression() instanceof MethodCallExpr) {
                        MethodCallExpr methodCall = (MethodCallExpr) castExpr.getExpression();
                        
                        if (methodCall.getNameAsString().equals("getLogger")) {
                            if (methodCall.getScope().isPresent() && 
                                methodCall.getScope().get() instanceof NameExpr) {
                                NameExpr scope = (NameExpr) methodCall.getScope().get();
                                
                                if (scope.getNameAsString().equals("LoggerFactory")) {
                                    System.out.println("Found problematic cast: " + castExpr);
                                    
                                    // Find the method call that uses this cast - it should be in the same statement
                                    MethodCallExpr setLevelCall = castExpr.findAncestor(com.github.javaparser.ast.stmt.ExpressionStmt.class)
                                            .flatMap(stmt -> stmt.findAll(MethodCallExpr.class).stream()
                                                    .filter(mc -> mc.getScope().isPresent() && mc.getScope().get().equals(castExpr))
                                                    .findFirst())
                                            .orElse(null);
                                    
                                    System.out.println("setLevelCall: " + setLevelCall);
                                    if (setLevelCall != null) {
                                        System.out.println("setLevelCall name: " + setLevelCall.getNameAsString());
                                    }
                                    
                                    if (setLevelCall != null && setLevelCall.getNameAsString().equals("setLevel")) {
                                        System.out.println("Found setLevel call: " + setLevelCall);
                                        
                                        // Get the parent statement
                                        Statement parentStatement = setLevelCall.findAncestor(Statement.class).orElse(null);
                                        if (parentStatement != null) {
                                            System.out.println("Parent statement: " + parentStatement);
                                            
                                            // Create simple replacement
                                            String replacement = "{\n    org.slf4j.Logger loggerTemp = LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);\n    if (loggerTemp instanceof ch.qos.logback.classic.Logger) {\n        ((ch.qos.logback.classic.Logger) loggerTemp).setLevel(Level.TRACE);\n    }\n}";
                                            
                                            try {
                                                BlockStmt replacementBlock = parser.parseBlock(replacement).getResult().orElse(null);
                                                if (replacementBlock != null) {
                                                    parentStatement.replace(replacementBlock);
                                                    System.out.println("Successfully replaced!");
                                                    wasModified[0] = true;
                                                }
                                            } catch (Exception e) {
                                                System.err.println("Error replacing: " + e.getMessage());
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        });
        
        if (wasModified[0]) {
            try (FileOutputStream fos = new FileOutputStream("../pdb/src/test/java/com/feedzai/commons/sql/abstraction/engine/impl/abs/EngineCloseTest.java")) {
                fos.write(cu.toString().getBytes());
            }
            System.out.println("File updated successfully!");
        }
    }
}