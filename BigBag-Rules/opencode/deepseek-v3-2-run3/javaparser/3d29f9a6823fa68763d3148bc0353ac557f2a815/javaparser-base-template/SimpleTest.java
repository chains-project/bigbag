import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.MethodCallExpr;

public class SimpleTest {
    public static void main(String[] args) {
        // Test the transformation logic
        testRemoveAddEnabledLanguages();
    }
    
    private static void testRemoveAddEnabledLanguages() {
        System.out.println("Testing addEnabledLanguages removal...");
        
        // Test case 1: Simple chain
        String code1 = "AnalysisEngineConfiguration.builder().addEnabledLanguages(langs).setClientPid(123).build();";
        
        // Test case 2: Multi-line
        String code2 = """
            AnalysisEngineConfiguration.builder()
                .addEnabledLanguages(langs)
                .setClientPid(123)
                .build();
            """;
        
        // Test case 3: With other methods before
        String code3 = "SomeOther.builder().something().addEnabledLanguages(langs).build();";
        
        JavaParser parser = new JavaParser();
        
        System.out.println("Test 1 - Single line:");
        System.out.println("Before: " + code1);
        // Would apply transformation here
        
        System.out.println("\nTest 2 - Multi-line:");
        System.out.println("Before:\n" + code2);
        // Would apply transformation here
        
        System.out.println("\nTest 3 - Other builder:");
        System.out.println("Before: " + code3);
        // Would apply transformation here (might be wrong if addEnabledLanguages exists elsewhere)
    }
}