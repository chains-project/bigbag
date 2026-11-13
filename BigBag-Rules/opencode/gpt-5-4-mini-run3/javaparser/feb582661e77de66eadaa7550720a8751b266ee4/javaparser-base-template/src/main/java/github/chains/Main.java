package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

public class Main {

    private static final String OLD_STRING_UTILS = "liquibase.util.StringUtils";
    private static final String NEW_STRING_UTILS = "liquibase.repackaged.org.apache.commons.lang3.StringUtils";
    private static final String EXECUTOR_SERVICE_FQCN = "liquibase.executor.ExecutorService";
    private static final String EXECUTOR_SERVICE_REPLACEMENT =
            "liquibase.Scope.getCurrentScope().getSingleton(liquibase.executor.ExecutorService.class)";

    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            throw new IllegalArgumentException("Usage: Main <sourceDir> [outputDir]");
        }

        Path sourceRoot = Paths.get(args[0]).toAbsolutePath().normalize();
        Path outputRoot = args.length > 1 ? Paths.get(args[1]).toAbsolutePath().normalize() : sourceRoot;

        List<Path> javaFiles;
        try (java.util.stream.Stream<Path> stream = Files.walk(sourceRoot)) {
            javaFiles = stream.filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                    .collect(Collectors.toList());
        }

        int rewrittenFiles = 0;
        for (Path sourceFile : javaFiles) {
            Path relative = sourceRoot.relativize(sourceFile);
            Path targetFile = outputRoot.resolve(relative);
            String original = Files.readString(sourceFile, StandardCharsets.UTF_8);
            String transformed = transformSource(original);

            if (!original.equals(transformed)) {
                rewrittenFiles++;
            }

            if (outputRoot.equals(sourceRoot)) {
                Files.writeString(sourceFile, transformed, StandardCharsets.UTF_8);
            } else {
                Files.createDirectories(targetFile.getParent());
                Files.writeString(targetFile, transformed, StandardCharsets.UTF_8);
            }
        }

        System.out.println("Rewritten files: " + rewrittenFiles);
    }

    private static String transformSource(String source) {
        CompilationUnit compilationUnit = StaticJavaParser.parse(source);
        LexicalPreservingPrinter.setup(compilationUnit);

        compilationUnit.getImports().stream()
                .filter(Main::isOldStringUtilsImport)
                .forEach(importDeclaration -> importDeclaration.setName(NEW_STRING_UTILS));

        compilationUnit.findAll(MethodCallExpr.class).stream()
                .filter(Main::isExecutorServiceGetInstanceCall)
                .forEach(methodCallExpr -> methodCallExpr.replace(StaticJavaParser.parseExpression(EXECUTOR_SERVICE_REPLACEMENT)));

        return LexicalPreservingPrinter.print(compilationUnit);
    }

    private static boolean isOldStringUtilsImport(ImportDeclaration importDeclaration) {
        return importDeclaration.getNameAsString().equals(OLD_STRING_UTILS);
    }

    private static boolean isExecutorServiceGetInstanceCall(MethodCallExpr methodCallExpr) {
        return methodCallExpr.getNameAsString().equals("getInstance")
                && methodCallExpr.getArguments().isEmpty()
                && methodCallExpr.getScope().map(Main::isExecutorServiceReference).orElse(false);
    }

    private static boolean isExecutorServiceReference(com.github.javaparser.ast.expr.Expression expression) {
        return expression.toString().equals("ExecutorService")
                || expression.toString().equals(EXECUTOR_SERVICE_FQCN)
                || expression.toString().endsWith(".ExecutorService");
    }
}
