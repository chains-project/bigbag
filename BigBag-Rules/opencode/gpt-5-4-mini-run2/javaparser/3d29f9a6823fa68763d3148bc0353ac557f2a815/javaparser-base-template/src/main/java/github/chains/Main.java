package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.stream.Stream;

public class Main {
    public static void main(String[] args) {
        if (args.length != 1) {
            throw new IllegalArgumentException("Expected exactly one argument: source root");
        }

        Path sourceRoot = Paths.get(args[0]);
        if (!Files.isDirectory(sourceRoot)) {
            throw new IllegalArgumentException(
                    "Source root does not exist or is not a directory: " + sourceRoot);
        }

        try (Stream<Path> paths = Files.walk(sourceRoot)) {
            paths.filter(path -> path.toString().endsWith(".java"))
                    .sorted(Comparator.naturalOrder())
                    .forEach(Main::transformFile);
        } catch (IOException e) {
            throw new RuntimeException("Failed to traverse source root: " + sourceRoot, e);
        }
    }

    private static void transformFile(Path file) {
        try {
            CompilationUnit compilationUnit =
                    StaticJavaParser.parse(Files.readString(file, StandardCharsets.UTF_8));
            LexicalPreservingPrinter.setup(compilationUnit);

            boolean[] changed = {false};
            compilationUnit.findAll(MethodCallExpr.class).stream()
                    .filter(Main::matchesOldPattern)
                    .forEach(
                            call -> {
                                Node parent = call.getParentNode().orElse(null);
                                if (parent instanceof ExpressionStmt) {
                                    parent.remove();
                                } else {
                                    call.getScope().ifPresent(scope -> call.replace(scope.clone()));
                                }
                                changed[0] = true;
                            });

            if (changed[0]) {
                Files.writeString(file, LexicalPreservingPrinter.print(compilationUnit), StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to transform file: " + file, e);
        }
    }

    private static boolean matchesOldPattern(MethodCallExpr call) {
        return call.getNameAsString().equals("addEnabledLanguages")
                && call.getArguments().size() == 1
                && call.getScope().isPresent();
    }
}
