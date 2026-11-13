package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.CastExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;

import java.io.FileInputStream;

public class AnalyzeMain {
    public static void main(String[] args) throws Exception {
        JavaParser parser = new JavaParser();
        CompilationUnit cu = parser.parse(new FileInputStream("../pdb/src/test/java/com/feedzai/commons/sql/abstraction/engine/impl/abs/EngineCloseTest.java")).getResult().orElse(null);
        
        if (cu == null) {
            System.out.println("Failed to parse file");
            return;
        }
        
        // Find all cast expressions
        cu.findAll(CastExpr.class).forEach(castExpr -> {
            System.out.println("=== Found cast: " + castExpr + " ===");
            
            // Find all method calls in the same statement
            castExpr.findAncestor(com.github.javaparser.ast.stmt.ExpressionStmt.class).ifPresent(stmt -> {
                System.out.println("Parent statement: " + stmt);
                
                // Find method calls in this statement
                stmt.findAll(MethodCallExpr.class).forEach(mc -> {
                    System.out.println("  Method call in statement: " + mc);
                    System.out.println("    Name: " + mc.getNameAsString());
                    if (mc.getScope().isPresent()) {
                        System.out.println("    Scope: " + mc.getScope().get());
                    }
                });
            });
            
            System.out.println();
        });
    }
}