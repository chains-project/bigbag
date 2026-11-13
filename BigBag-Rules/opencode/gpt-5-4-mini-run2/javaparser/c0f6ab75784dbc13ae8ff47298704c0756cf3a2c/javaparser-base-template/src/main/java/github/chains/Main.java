package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

public class Main {
    private static final String TARGET_METHOD = "addEnabledLanguages";

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            throw new IllegalArgumentException("Expected a single source root path");
        }

        Path sourceRoot = Paths.get(args[0]);
        List<Path> javaFiles;
        try (var paths = Files.walk(sourceRoot)) {
            javaFiles = paths.filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                    .collect(Collectors.toList());
        }

        for (Path javaFile : javaFiles) {
            CompilationUnit compilationUnit = StaticJavaParser.parse(javaFile);
            LexicalPreservingPrinter.setup(compilationUnit);

            boolean changed = false;
            for (MethodCallExpr methodCall : compilationUnit.findAll(MethodCallExpr.class)) {
                if (shouldRewrite(methodCall)) {
                    methodCall.replace(methodCall.getScope().orElseThrow().clone());
                    changed = true;
                }
            }

            if (changed) {
                Files.writeString(javaFile, LexicalPreservingPrinter.print(compilationUnit));
            }
        }
    }

    private static boolean shouldRewrite(MethodCallExpr methodCall) {
        return TARGET_METHOD.equals(methodCall.getNameAsString()) && methodCall.getArguments().size() == 1;
    }
}
