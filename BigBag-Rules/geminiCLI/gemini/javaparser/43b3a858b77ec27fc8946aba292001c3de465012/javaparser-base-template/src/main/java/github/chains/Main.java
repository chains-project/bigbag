package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.ImportDeclaration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

public class Main {
    public static void main(String[] args) throws IOException {
        List<String> files = Arrays.asList(
            "/workspace/pdb/src/test/java/com/feedzai/commons/sql/abstraction/engine/impl/abs/BatchUpdateTest.java",
            "/workspace/pdb/src/test/java/com/feedzai/commons/sql/abstraction/engine/impl/abs/EngineCloseTest.java",
            "/workspace/pdb/src/test/java/com/feedzai/commons/sql/abstraction/engine/impl/abs/EngineGeneralTest.java"
        );

        for (String file : files) {
            System.out.println("Processing " + file);
            CompilationUnit cu = StaticJavaParser.parse(Files.newInputStream(Paths.get(file)));

            // Remove initStatic method annotated with @BeforeClass
            cu.findAll(MethodDeclaration.class).stream()
                .filter(m -> m.getNameAsString().equals("initStatic"))
                .filter(m -> m.getAnnotationByName("BeforeClass").isPresent())
                .forEach(m -> {
                    System.out.println("Removing method " + m.getNameAsString());
                    m.remove();
                });

            // Remove logback imports
            cu.findAll(ImportDeclaration.class).stream()
                .filter(i -> i.getNameAsString().startsWith("ch.qos.logback"))
                .forEach(i -> {
                    System.out.println("Removing import " + i.getNameAsString());
                    i.remove();
                });
            
            Files.write(Paths.get(file), cu.toString().getBytes());
        }
    }
}