package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Simple test for HamcrestTransformation class.
 */
public class HamcrestTransformationSimpleTest {
    
    public static void main(String[] args) {
        System.out.println("Running HamcrestTransformation simple test...");
        
        try {
            // Test with a sample file
            String testFile = "HamcrestTransformationTestFile.java";
            
            // Create a temporary file with hamcrest imports
            String content = "import org.hamcrest.library.Matchers;\n" +
                             "import org.hamcrest.core.IsEqual;\n" +
                             "public class TestClass {\n" +
                             "    public void test() {\n" +
                             "        // Some code here\n" +
                             "    }\n" +
                             "}\n";
            
            Files.write(Paths.get(testFile), content.getBytes());
            
            // Check if file has hamcrest imports
            if (HamcrestTransformation.hasHamcrestImports(testFile)) {
                System.out.println("✓ Found hamcrest imports in test file");
                HamcrestTransformation.applyTransformation(testFile);
                System.out.println("✓ Applied transformation successfully");
                
                // Check the result
                String transformedContent = new String(Files.readAllBytes(Paths.get(testFile)));
                if (transformedContent.contains("import org.hamcrest.Matchers;") &&
                    transformedContent.contains("import org.hamcrest.IsEqual;")) {
                    System.out.println("✓ Transformation correctly updated imports");
                } else {
                    System.out.println("✗ Transformation did not update imports correctly");
                }
            } else {
                System.out.println("✗ Did not find hamcrest imports in test file");
            }
            
            // Cleanup
            Files.deleteIfExists(Paths.get(testFile));
            
            System.out.println("Test completed.");
        } catch (Exception e) {
            System.err.println("Test failed: " + e.getMessage());
            e.printStackTrace();
        }
    }
}