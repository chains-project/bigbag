package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.LambdaExpr;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Main {
    public static void main(String[] args) {
        if (args.length != 1) {
            throw new IllegalArgumentException("Expected a single source directory argument");
        }

        Path sourceRoot = Paths.get(args[0]);
        try {
            Files.walk(sourceRoot)
                    .filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                    .forEach(Main::transformFile);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void transformFile(Path path) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(path, StandardCharsets.UTF_8);
            LexicalPreservingPrinter.setup(cu);

            boolean[] changed = {false};

            cu.getImports().stream()
                    .filter(importDeclaration -> importDeclaration.getNameAsString().equals("org.yaml.snakeyaml.inspector.TrustedTagInspector"))
                    .findFirst()
                    .ifPresent(importDeclaration -> {
                        importDeclaration.remove();
                        changed[0] = true;
                    });

            cu.findAll(ObjectCreationExpr.class).forEach(objectCreationExpr -> {
                String typeName = objectCreationExpr.getType().getNameAsString();
                String qualifiedName = objectCreationExpr.getType().asString();
                if (!typeName.equals("TrustedTagInspector")
                        && !qualifiedName.equals("org.yaml.snakeyaml.inspector.TrustedTagInspector")) {
                    return;
                }

                LambdaExpr replacement = StaticJavaParser.parseExpression("tag -> true").asLambdaExpr();
                objectCreationExpr.replace(replacement);
                changed[0] = true;
            });

            if (changed[0]) {
                Files.writeString(path, LexicalPreservingPrinter.print(cu), StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to transform " + path, e);
        }
    }
}
