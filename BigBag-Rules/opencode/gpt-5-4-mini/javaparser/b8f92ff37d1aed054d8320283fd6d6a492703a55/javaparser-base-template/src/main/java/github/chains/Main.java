package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Main {
    public static void main(String[] args) {
        if (args.length == 0) {
            throw new IllegalArgumentException("Usage: Main <source-root>");
        }

        Path sourceRoot = Paths.get(args[0]);
        if (!Files.isDirectory(sourceRoot)) {
            throw new IllegalArgumentException("Source root does not exist: " + sourceRoot);
        }

        try {
            Files.walk(sourceRoot)
                    .filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                    .forEach(Main::transformFile);
        } catch (IOException e) {
            throw new RuntimeException("Failed to traverse " + sourceRoot, e);
        }
    }

    private static void transformFile(Path javaFile) {
        try {
            CompilationUnit compilationUnit = StaticJavaParser.parse(javaFile);
            LexicalPreservingPrinter.setup(compilationUnit);

            boolean changed = false;
            for (MethodCallExpr call : compilationUnit.findAll(MethodCallExpr.class)) {
                if (isRemovedWildcardCall(compilationUnit, call)) {
                    call.replace(StaticJavaParser.parseExpression(
                            "new org.apache.maven.surefire.api.testset.TestListResolver(\"*.class\")"));
                    changed = true;
                }
            }

            if (changed) {
                Files.writeString(javaFile, LexicalPreservingPrinter.print(compilationUnit), StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to transform " + javaFile, e);
        }
    }

    private static boolean isRemovedWildcardCall(CompilationUnit compilationUnit, MethodCallExpr call) {
        if (!"getWildcard".equals(call.getNameAsString()) || !call.getArguments().isEmpty()) {
            return false;
        }

        if (call.getScope().isPresent()) {
            String scope = call.getScope().get().toString();
            return scope.endsWith("TestListResolver")
                    || scope.equals("org.apache.maven.surefire.api.testset.TestListResolver");
        }

        return importsTestListResolver(compilationUnit);
    }

    private static boolean importsTestListResolver(CompilationUnit compilationUnit) {
        return compilationUnit.getImports().stream().anyMatch(importDecl -> {
            String name = importDecl.getNameAsString();
            return name.equals("org.apache.maven.surefire.api.testset.TestListResolver")
                    || (importDecl.isStatic() && name.equals(
                    "org.apache.maven.surefire.api.testset.TestListResolver.getWildcard"));
        });
    }
}
