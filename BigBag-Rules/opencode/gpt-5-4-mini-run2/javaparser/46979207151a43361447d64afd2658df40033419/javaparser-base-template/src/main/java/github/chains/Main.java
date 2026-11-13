package github.chains;

import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.Range;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class Main {
    public static void main(String[] args) {
        Path sourceRoot = Paths.get(args.length > 0 ? args[0] : ".").toAbsolutePath().normalize();

        StaticJavaParser.setConfiguration(new ParserConfiguration());

        try {
            Files.walk(sourceRoot)
                    .filter(path -> path.toString().endsWith(".java"))
                    .forEach(path -> transformFile(path));
        } catch (IOException e) {
            throw new RuntimeException("Failed to traverse source tree: " + sourceRoot, e);
        }
    }

    private static void transformFile(Path path) {
        try {
            String original = Files.readString(path);
            CompilationUnit compilationUnit = StaticJavaParser.parse(original);

            AtomicBoolean changed = new AtomicBoolean(false);
            List<Replacement> replacements = new ArrayList<>();
            compilationUnit.findAll(MethodCallExpr.class).forEach(methodCallExpr -> {
                if (!"getInstance".equals(methodCallExpr.getNameAsString()) || methodCallExpr.getArguments().size() != 0) {
                    return;
                }

                if (!isSingletonGetterWithStrategyScope(methodCallExpr)) {
                    return;
                }

                Expression scope = methodCallExpr.getScope().orElse(null);
                if (scope == null) {
                    return;
                }

                Range scopeRange = scope.getRange().orElse(null);
                Range callRange = methodCallExpr.getRange().orElse(null);
                if (scopeRange == null || callRange == null) {
                    return;
                }

                int start = toOffset(original, scopeRange.begin.line, scopeRange.begin.column);
                int scopeEnd = toOffset(original, scopeRange.end.line, scopeRange.end.column) + 1;
                int end = toOffset(original, callRange.end.line, callRange.end.column) + 1;
                replacements.add(new Replacement(start, end, original.substring(start, scopeEnd) + ".INSTANCE"));
                changed.set(true);
            });

            if (changed.get()) {
                String updated = original;
                replacements.sort(Comparator.comparingInt((Replacement r) -> r.start).reversed());
                for (Replacement replacement : replacements) {
                    updated = updated.substring(0, replacement.start) + replacement.text + updated.substring(replacement.end);
                }
                Files.writeString(path, updated);
            }
        } catch (Exception ignored) {
            // Skip files that cannot be parsed.
        }
    }

    private static boolean isSingletonGetterWithStrategyScope(MethodCallExpr methodCallExpr) {
        Expression scope = methodCallExpr.getScope().orElse(null);
        if (scope == null) {
            return false;
        }

        if (scope.isNameExpr()) {
            return scope.asNameExpr().getNameAsString().endsWith("Strategy");
        }

        if (scope.isFieldAccessExpr()) {
            return scope.asFieldAccessExpr().getNameAsString().endsWith("Strategy");
        }

        return false;
    }

    private static int toOffset(String source, int line, int column) {
        int offset = 0;
        int currentLine = 1;
        while (currentLine < line && offset < source.length()) {
            if (source.charAt(offset++) == '\n') {
                currentLine++;
            }
        }
        return offset + column - 1;
    }

    private static final class Replacement {
        private final int start;
        private final int end;
        private final String text;

        private Replacement(int start, int end, String text) {
            this.start = start;
            this.end = end;
            this.text = text;
        }
    }
}
