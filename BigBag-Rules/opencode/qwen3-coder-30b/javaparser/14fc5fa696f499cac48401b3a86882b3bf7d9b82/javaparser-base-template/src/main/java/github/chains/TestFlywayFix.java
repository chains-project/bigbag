package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import java.io.File;
import java.io.IOException;

public class TestFlywayFix {
    public static void main(String[] args) throws IOException {
        // Test with our sample file
        File testFile = new File("/workspace/test-flyway.java");
        CompilationUnit cu = StaticJavaParser.parse(testFile);
        
        final boolean[] modified = {false};
        
        cu.accept(new VoidVisitorAdapter<Void>() {
            @Override
            public void visit(ObjectCreationExpr n, Void arg) {
                super.visit(n, arg);
                
                // Check if this is a Flyway object creation with no arguments
                if (n.getType() != null && n.getType().getNameAsString().equals("Flyway")) {
                    // Check if it's a no-argument constructor
                    if (n.getArguments().isEmpty()) {
                        System.out.println("Found Flyway constructor call without arguments");
                        modified[0] = true;
                    }
                }
            }
        }, null);
        
        if (modified[0]) {
            System.out.println("Found and would modify Flyway constructor calls");
        } else {
            System.out.println("No Flyway constructor calls found");
        }
    }
}