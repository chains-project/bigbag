package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.CastExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;

import java.io.FileInputStream;

public class DebugMain {
    public static void main(String[] args) throws Exception {
        JavaParser parser = new JavaParser();
        CompilationUnit cu = parser.parse(new FileInputStream("../pdb/src/test/java/com/feedzai/commons/sql/abstraction/engine/impl/abs/EngineCloseTest.java")).getResult().orElse(null);
        
        if (cu == null) {
            System.out.println("Failed to parse file");
            return;
        }
        
        // Find all cast expressions
        cu.findAll(CastExpr.class).forEach(castExpr -> {
            System.out.println("Found cast: " + castExpr);
            
            // Check if this is a cast to Logger
            if (castExpr.getType() instanceof ClassOrInterfaceType) {
                ClassOrInterfaceType type = (ClassOrInterfaceType) castExpr.getType();
                System.out.println("  Cast type: " + type.getNameAsString());
                
                if (type.getNameAsString().equals("Logger")) {
                    System.out.println("  Matches Logger type!");
                    
                    // Check if the expression being cast is LoggerFactory.getLogger()
                    if (castExpr.getExpression() instanceof MethodCallExpr) {
                        MethodCallExpr methodCall = (MethodCallExpr) castExpr.getExpression();
                        System.out.println("  Method call: " + methodCall.getNameAsString());
                        
                        if (methodCall.getNameAsString().equals("getLogger")) {
                            System.out.println("  Matches getLogger method!");
                            
                            if (methodCall.getScope().isPresent() && 
                                methodCall.getScope().get() instanceof NameExpr) {
                                NameExpr scope = (NameExpr) methodCall.getScope().get();
                                System.out.println("  Scope: " + scope.getNameAsString());
                                
                                if (scope.getNameAsString().equals("LoggerFactory")) {
                                    System.out.println("  MATCH FOUND! This is the problematic cast!");
                                }
                            }
                        }
                    }
                }
            }
        });
    }
}