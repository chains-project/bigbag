package github.chains;

import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

public class Main {
    public static void main(String[] args) {
        if (args.length != 1) {
            throw new IllegalArgumentException("Usage: Main <source-root>");
        }

        StaticJavaParser.getParserConfiguration()
                .setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_11);

        Path sourceRoot = Paths.get(args[0]);
        try (Stream<Path> paths = Files.walk(sourceRoot)) {
            paths.filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                    .forEach(Main::rewriteFile);
        } catch (IOException e) {
            throw new RuntimeException("Failed to walk source tree: " + sourceRoot, e);
        }
    }

    private static void rewriteFile(Path path) {
        try {
            CompilationUnit compilationUnit = StaticJavaParser.parse(path);
            LexicalPreservingPrinter.setup(compilationUnit);

            compilationUnit.findAll(MethodCallExpr.class).stream()
                    .filter(call -> call.getNameAsString().equals("addEnabledLanguages"))
                    .filter(call -> call.getArguments().size() == 1)
                    .forEach(Main::rewriteAddEnabledLanguagesCall);

            Files.writeString(path, LexicalPreservingPrinter.print(compilationUnit));
        } catch (IOException e) {
            throw new RuntimeException("Failed to rewrite " + path, e);
        }
    }

    private static void rewriteAddEnabledLanguagesCall(MethodCallExpr call) {
        if (call.getParentNode().filter(ExpressionStmt.class::isInstance).isPresent()) {
            call.findAncestor(ExpressionStmt.class).ifPresent(ExpressionStmt::remove);
            return;
        }

        call.getScope().ifPresent(scope -> call.replace(scope.clone()));
    }
}
