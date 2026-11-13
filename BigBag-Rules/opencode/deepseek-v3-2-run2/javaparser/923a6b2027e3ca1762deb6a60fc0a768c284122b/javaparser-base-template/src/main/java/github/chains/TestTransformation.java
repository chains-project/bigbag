package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.expr.Name;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

public class TestTransformation {
    public static void main(String[] args) throws IOException {
        // Test the transformation logic directly
        String testCode = "package test;\n" +
                         "import org.hamcrest.core.IsAnything;\n" +
                         "import org.hamcrest.CustomMatcher;\n" +
                         "import org.hamcrest.Matchers;\n" +
                         "import org.hamcrest.MatcherAssert;\n" +
                         "public class TestHamcrest {}\n";
        
        CompilationUnit cu = new JavaParser().parse(testCode).getResult().orElseThrow();
        
        System.out.println("Original imports:");
        for (ImportDeclaration importDecl : cu.getImports()) {
            System.out.println("  " + importDecl.getNameAsString());
        }
        
        // Apply transformation
        cu.accept(new Main.HamcrestImportTransformer(), null);
        
        System.out.println("\nTransformed imports:");
        for (ImportDeclaration importDecl : cu.getImports()) {
            System.out.println("  " + importDecl.getNameAsString());
        }
        
        // Check if transformation worked
        boolean foundIsAnything = false;
        for (ImportDeclaration importDecl : cu.getImports()) {
            if (importDecl.getNameAsString().equals("org.hamcrest.IsAnything")) {
                foundIsAnything = true;
                break;
            }
        }
        
        if (foundIsAnything) {
            System.out.println("\nSUCCESS: IsAnything was transformed from org.hamcrest.core.IsAnything to org.hamcrest.IsAnything");
        } else {
            System.out.println("\nWARNING: IsAnything transformation did not occur as expected");
        }
    }
}