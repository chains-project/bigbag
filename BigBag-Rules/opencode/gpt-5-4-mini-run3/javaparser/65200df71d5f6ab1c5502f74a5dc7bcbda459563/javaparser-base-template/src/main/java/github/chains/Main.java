package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

public class Main {
    private static final String REMOVED_METHOD = "addEnabledLanguages";

    public static void main(String[] args) {
        if (args.length != 1) {
            throw new IllegalArgumentException("Expected exactly one argument: source directory");
        }

        Path sourceRoot = Paths.get(args[0]);
        if (!Files.isDirectory(sourceRoot)) {
            throw new IllegalArgumentException("Source directory does not exist: " + sourceRoot);
        }

        try {
            rewriteProject(sourceRoot);
        } catch (IOException e) {
            throw new RuntimeException("Failed to rewrite sources", e);
        }
    }

    private static void rewriteProject(Path sourceRoot) throws IOException {
        try (Stream<Path> paths = Files.walk(sourceRoot)) {
            paths.filter(path -> path.toString().endsWith(".java"))
                    .forEach(
                            path -> {
                                try {
                                    rewriteFile(path);
                                } catch (IOException e) {
                                    throw new RuntimeException("Failed to process " + path, e);
                                }
                            });
        }
    }

    private static void rewriteFile(Path file) throws IOException {
        CompilationUnit compilationUnit = StaticJavaParser.parse(file);
        boolean changed = false;

        for (MethodCallExpr call : compilationUnit.findAll(MethodCallExpr.class)) {
            if (REMOVED_METHOD.equals(call.getNameAsString()) && call.getArguments().size() == 1) {
                Expression scope = call.getScope().orElse(null);
                if (scope != null) {
                    call.replace(scope.clone());
                    changed = true;
                }
            }
        }

        if (changed) {
            Files.writeString(file, compilationUnit.toString());
        }
    }
}
