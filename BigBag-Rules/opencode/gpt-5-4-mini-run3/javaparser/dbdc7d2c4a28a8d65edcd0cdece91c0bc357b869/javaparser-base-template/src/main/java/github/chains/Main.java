package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

public class Main {
    private static final String REMOVED_METHOD_NAME = "enableLogging";

    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Usage: Main <source-root>");
            System.exit(1);
        }

        Path sourceRoot = Paths.get(args[0]);
        try (Stream<Path> paths = Files.walk(sourceRoot)) {
            paths.filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                .forEach(Main::transformFile);
        } catch (IOException e) {
            throw new RuntimeException("Failed to traverse source tree: " + sourceRoot, e);
        }
    }

    private static void transformFile(Path sourceFile) {
        try {
            CompilationUnit compilationUnit = StaticJavaParser.parse(sourceFile);
            LexicalPreservingPrinter.setup(compilationUnit);
            AtomicBoolean changed = new AtomicBoolean(false);

            compilationUnit.findAll(MethodCallExpr.class).forEach(methodCall -> {
                if (!REMOVED_METHOD_NAME.equals(methodCall.getNameAsString()) || methodCall.getArguments().size() != 1) {
                    return;
                }

                if (methodCall.getParentNode().filter(ExpressionStmt.class::isInstance).isPresent()) {
                    methodCall.findAncestor(ExpressionStmt.class).ifPresent(node -> node.remove());
                    changed.set(true);
                }
            });

            if (changed.get()) {
                Files.write(sourceFile, LexicalPreservingPrinter.print(compilationUnit).getBytes(StandardCharsets.UTF_8));
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to transform " + sourceFile, e);
        }
    }
}
