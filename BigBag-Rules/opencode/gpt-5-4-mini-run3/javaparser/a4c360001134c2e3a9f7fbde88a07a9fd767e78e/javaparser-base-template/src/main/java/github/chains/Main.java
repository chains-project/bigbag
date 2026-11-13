package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

public class Main {
    private static final String OLD_TYPE = "org.reflections.util.FilterBuilder";
    private static final String OLD_METHOD = "apply";
    private static final String NEW_METHOD = "test";

    public static void main(String[] args) throws IOException {
        if (args.length == 0) {
            throw new IllegalArgumentException("Expected a source directory path");
        }

        Path sourceRoot = Paths.get(args[0]);
        try (Stream<Path> paths = Files.walk(sourceRoot)) {
            paths.filter(path -> path.toString().endsWith(".java"))
                    .forEach(Main::rewriteFile);
        }
    }

    private static void rewriteFile(Path path) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(path);
            LexicalPreservingPrinter.setup(cu);

            boolean[] changed = {false};
            cu.findAll(MethodCallExpr.class).forEach(call -> {
                if (shouldRewrite(call)) {
                    call.setName(NEW_METHOD);
                    changed[0] = true;
                }
            });

            if (changed[0]) {
                Files.write(path, LexicalPreservingPrinter.print(cu).getBytes(StandardCharsets.UTF_8));
            }
        } catch (Exception ex) {
            throw new RuntimeException("Failed to rewrite " + path, ex);
        }
    }

    private static boolean shouldRewrite(MethodCallExpr call) {
        if (!OLD_METHOD.equals(call.getNameAsString()) || call.getArguments().size() != 1) {
            return false;
        }
        return call.getScope().map(Main::isFilterBuilderChain).orElse(false);
    }

    private static boolean isFilterBuilderChain(Expression expression) {
        if (expression.isObjectCreationExpr()) {
            return expression.toString().startsWith("new FilterBuilder(")
                    || expression.toString().contains(OLD_TYPE);
        }

        if (expression.isMethodCallExpr()) {
            MethodCallExpr methodCallExpr = expression.asMethodCallExpr();
            if (methodCallExpr.getScope().isPresent() && isFilterBuilderChain(methodCallExpr.getScope().get())) {
                return true;
            }
            return methodCallExpr.toString().contains("FilterBuilder");
        }

        String text = expression.toString();
        return text.contains("FilterBuilder") || text.contains(OLD_TYPE);
    }
}
