import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.MethodCallExpr;

public class DebugTransformation {
    public static void main(String[] args) {
        // Test case 1: Multi-line chain
        String code1 = """
            AnalysisEngineConfiguration.builder()
                .addEnabledLanguages(someSet)
                .setClientPid(123)
                .build();
            """;
        
        // Test case 2: Single-line chain  
        String code2 = "AnalysisEngineConfiguration.builder().addEnabledLanguages(someSet).setClientPid(123).build();";
        
        JavaParser parser = new JavaParser();
        
        System.out.println("Testing multi-line:");
        testTransformation(parser, code1);
        
        System.out.println("\nTesting single-line:");
        testTransformation(parser, code2);
    }
    
    private static void testTransformation(JavaParser parser, String code) {
        CompilationUnit cu = parser.parse(code).getResult().orElse(null);
        
        // Print AST structure
        cu.accept(new com.github.javaparser.ast.visitor.VoidVisitorAdapter<Void>() {
            @Override
            public void visit(MethodCallExpr n, Void arg) {
                System.out.println("Method: " + n.getName() + 
                    ", hasScope: " + n.getScope().isPresent() +
                    ", scope: " + (n.getScope().isPresent() ? n.getScope().get().getClass().getSimpleName() : "null"));
                super.visit(n, arg);
            }
        }, null);
    }
}