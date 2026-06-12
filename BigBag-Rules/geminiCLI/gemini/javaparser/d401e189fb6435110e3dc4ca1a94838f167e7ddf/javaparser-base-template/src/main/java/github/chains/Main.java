package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

public class Main {
    public static void main(String[] args) throws IOException {
        List<String> files = Arrays.asList(
            "/workspace/pdb/src/test/java/com/feedzai/commons/sql/abstraction/engine/impl/abs/EngineGeneralTest.java",
            "/workspace/pdb/src/test/java/com/feedzai/commons/sql/abstraction/engine/impl/abs/EngineCloseTest.java",
            "/workspace/pdb/src/test/java/com/feedzai/commons/sql/abstraction/engine/impl/abs/BatchUpdateTest.java"
        );

        for (String filePath : files) {
            System.out.println("Processing " + filePath);
            File file = new File(filePath);
            if (!file.exists()) {
                System.out.println("File not found: " + filePath);
                continue;
            }

            CompilationUnit cu = StaticJavaParser.parse(file);

            // Remove imports
            cu.findAll(ImportDeclaration.class).forEach(importDecl -> {
                String name = importDecl.getNameAsString();
                if (name.equals("ch.qos.logback.classic.Level") || name.equals("ch.qos.logback.classic.Logger")) {
                    importDecl.remove();
                }
            });

            // Remove initStatic method annotated with @BeforeClass
            cu.findAll(MethodDeclaration.class).forEach(method -> {
                if (method.getNameAsString().equals("initStatic")) {
                    for (AnnotationExpr annotation : method.getAnnotations()) {
                        if (annotation.getNameAsString().equals("BeforeClass")) {
                            method.remove();
                        }
                    }
                }
            });

            Files.write(Paths.get(filePath), cu.toString().getBytes());
        }
    }
}