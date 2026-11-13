package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.MethodCallExpr;

public class TestTransformation {
    public static void main(String[] args) {
        String code = """
            import java.util.Set;
            
            public class Test {
                public void testMethod() {
                    var config = AnalysisEngineConfiguration.builder()
                            .addEnabledLanguages(someSet)
                            .setClientPid(123)
                            .setExtraProperties(props)
                            .build();
                }
            }
            """;
            
        JavaParser parser = new JavaParser();
        CompilationUnit cu = parser.parse(code).getResult().orElse(null);
        
        System.out.println("Original:");
        System.out.println(cu);
        
        // Let's examine the AST structure
        cu.accept(new com.github.javaparser.ast.visitor.VoidVisitorAdapter<Void>() {
            @Override
            public void visit(MethodCallExpr n, Void arg) {
                System.out.println("Method call: " + n.getName() + 
                    ", scope: " + n.getScope().orElse(null) +
                    ", parent: " + n.getParentNode().orElse(null).getClass().getSimpleName());
                super.visit(n, arg);
            }
        }, null);
    }
}