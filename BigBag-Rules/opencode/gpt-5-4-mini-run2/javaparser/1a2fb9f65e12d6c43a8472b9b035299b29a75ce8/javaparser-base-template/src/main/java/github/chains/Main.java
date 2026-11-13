package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.Name;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

public class Main {
    private static final String OLD_PREFIX = "javax.validation";
    private static final String NEW_PREFIX = "jakarta.validation";

    public static void main(String[] args) throws IOException {
        Path sourceRoot = args.length > 0 ? Paths.get(args[0]) : Paths.get(".");
        sourceRoot = sourceRoot.toAbsolutePath().normalize();

        try (Stream<Path> paths = Files.walk(sourceRoot)) {
            paths.filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                .forEach(path -> {
                    try {
                        transform(path);
                    } catch (IOException e) {
                        throw new RuntimeException("Failed to transform " + path, e);
                    }
                });
        }
    }

    private static void transform(Path file) throws IOException {
        CompilationUnit compilationUnit = StaticJavaParser.parse(file.toFile());
        LexicalPreservingPrinter.setup(compilationUnit);

        boolean changed = false;

        for (ImportDeclaration importDeclaration : compilationUnit.getImports()) {
            String importName = importDeclaration.getNameAsString();
            if (importName.startsWith(OLD_PREFIX)) {
                importDeclaration.setName(StaticJavaParser.parseName(importName.replace(OLD_PREFIX, NEW_PREFIX)));
                changed = true;
            }
        }

        for (Name name : compilationUnit.findAll(Name.class)) {
            String qualifiedName = name.asString();
            if (qualifiedName.startsWith(OLD_PREFIX)) {
                name.replace(StaticJavaParser.parseName(qualifiedName.replace(OLD_PREFIX, NEW_PREFIX)));
                changed = true;
            }
        }

        for (MethodCallExpr methodCall : compilationUnit.findAll(MethodCallExpr.class)) {
            if (!"getValidator".equals(methodCall.getNameAsString()) || !methodCall.getScope().isPresent()) {
                continue;
            }

            Expression scope = methodCall.getScope().get();
            if (scope.toString().endsWith("validatorFactory")) {
                methodCall.setScope(StaticJavaParser.parseExpression("jakarta.validation.Validation.buildDefaultValidatorFactory()"));
                changed = true;
            }
        }

        if (changed) {
            Files.writeString(file, LexicalPreservingPrinter.print(compilationUnit));
        }
    }
}
