package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import java.io.File;
import java.nio.file.Files;

public class Main {
    public static void main(String[] args) throws Exception {
        File file = new File("/workspace/junit-quickcheck/core/src/test/java/com/pholser/junit/quickcheck/internal/generator/RegisterGeneratorsByConventionTest.java");
        CompilationUnit cu = StaticJavaParser.parse(file);

        for (ImportDeclaration id : cu.findAll(ImportDeclaration.class)) {
            if (id.getNameAsString().equals("org.mockito.runners.MockitoJUnitRunner")) {
                id.setName("org.mockito.junit.MockitoJUnitRunner");
            }
        }

        Files.write(file.toPath(), cu.toString().getBytes());
    }
}
