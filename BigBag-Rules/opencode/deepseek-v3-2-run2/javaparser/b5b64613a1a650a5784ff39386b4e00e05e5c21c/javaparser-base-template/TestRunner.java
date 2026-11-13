import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;

import java.io.StringReader;

public class TestRunner {
    public static void main(String[] args) throws Exception {
        String testCode = """
            import org.apache.commons.lang3.StringUtils;
            
            public class TestFile {
                public void testStringUtils() {
                    boolean result1 = StringUtils.isAllBlank("a", "b", "c");
                    boolean result2 = StringUtils.isAllBlank("", null, " ");
                }
                
                public static boolean isAllBlank(String a, String b) {
                    return StringUtils.isAllBlank(a, b);
                }
            }
            """;
            
        CompilationUnit cu = new JavaParser().parse(new StringReader(testCode)).getResult().get();
        
        System.out.println("Original code:");
        System.out.println(cu);
        
        // Simple transformer
        cu.accept(new ModifierVisitor<Void>() {
            @Override
            public Visitable visit(MethodCallExpr n, Void arg) {
                if (n.getNameAsString().equals("isAllBlank")) {
                    System.out.println("Found isAllBlank call: " + n);
                    // Would transform here
                }
                return super.visit(n, arg);
            }
        }, null);
        
        System.out.println("\nTransformed code (would be):");
        System.out.println("// isAllBlank would be transformed to compound isBlank checks");
    }
}
