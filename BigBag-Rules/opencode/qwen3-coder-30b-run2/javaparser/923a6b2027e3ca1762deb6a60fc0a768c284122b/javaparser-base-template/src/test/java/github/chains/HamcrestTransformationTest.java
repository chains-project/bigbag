package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

public class HamcrestTransformationTest {

    @Test
    public void testTransformHamcrestLibraryImport() throws IOException {
        // Test file content with hamcrest-library import
        String originalContent = 
            "import org.hamcrest.library.Matchers;\n" +
            "import org.hamcrest.library.CoreMatchers;\n" +
            "import org.hamcrest.library.deprecated.HamcrestLibraryIsDeprecated;\n" +
            "\n" +
            "public class TestClass {\n" +
            "    public void testMethod() {\n" +
            "        // Some test code\n" +
            "    }\n" +
            "}\n";
            
        String transformedContent = HamcrestTransformation.applyTransformation(originalContent);
        
        assertTrue(transformedContent.contains("import org.hamcrest.Matchers;"));
        assertTrue(transformedContent.contains("import org.hamcrest.CoreMatchers;"));
        assertTrue(transformedContent.contains("import org.hamcrest.library.deprecated.HamcrestLibraryIsDeprecated;"));
    }
    
    @Test
    public void testTransformHamcrestCoreImport() throws IOException {
        // Test file content with hamcrest-core import
        String originalContent = 
            "import org.hamcrest.core.IsEqual;\n" +
            "import org.hamcrest.core.IsNull;\n" +
            "\n" +
            "public class TestClass {\n" +
            "    public void testMethod() {\n" +
            "        // Some test code\n" +
            "    }\n" +
            "}\n";
            
        String transformedContent = HamcrestTransformation.applyTransformation(originalContent);
        
        assertTrue(transformedContent.contains("import org.hamcrest.IsEqual;"));
        assertTrue(transformedContent.contains("import org.hamcrest.IsNull;"));
    }
    
    @Test
    public void testNoHamcrestImports() throws IOException {
        // Test file content without hamcrest imports
        String originalContent = 
            "import java.util.List;\n" +
            "import java.util.Map;\n" +
            "\n" +
            "public class TestClass {\n" +
            "    public void testMethod() {\n" +
            "        // Some test code\n" +
            "    }\n" +
            "}\n";
            
        String transformedContent = HamcrestTransformation.applyTransformation(originalContent);
        
        assertEquals(originalContent, transformedContent);
    }
    
    @Test
    public void testHasHamcrestLibraryImports() throws IOException {
        // Test with hamcrest library imports
        String contentWithImports = 
            "import org.hamcrest.library.Matchers;\n" +
            "import org.hamcrest.library.CoreMatchers;\n" +
            "\n" +
            "public class TestClass {\n" +
            "    public void testMethod() {\n" +
            "        // Some test code\n" +
            "    }\n" +
            "}\n";
        
        // Write to temporary file
        Path tempFile = Files.createTempFile("test", ".java");
        Files.write(tempFile, contentWithImports.getBytes());
        
        assertTrue(HamcrestTransformation.hasHamcrestLibraryImports(tempFile.toString()));
        
        // Cleanup
        Files.delete(tempFile);
    }
    
    @Test
    public void testHasNoHamcrestLibraryImports() throws IOException {
        // Test without hamcrest library imports
        String contentWithoutImports = 
            "import java.util.List;\n" +
            "import java.util.Map;\n" +
            "\n" +
            "public class TestClass {\n" +
            "    public void testMethod() {\n" +
            "        // Some test code\n" +
            "    }\n" +
            "}\n";
        
        // Write to temporary file
        Path tempFile = Files.createTempFile("test", ".java");
        Files.write(tempFile, contentWithoutImports.getBytes());
        
        assertFalse(HamcrestTransformation.hasHamcrestLibraryImports(tempFile.toString()));
        
        // Cleanup
        Files.delete(tempFile);
    }
}