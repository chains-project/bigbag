package github.chains;

import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.CastExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;

public class Main {

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            throw new IllegalArgumentException("Expected a single source directory argument");
        }

        Path sourceRoot = Paths.get(args[0]);
        if (!Files.isDirectory(sourceRoot)) {
            throw new IllegalArgumentException("Not a directory: " + sourceRoot);
        }

        StaticJavaParser.getConfiguration().setCharacterEncoding(StandardCharsets.UTF_8);
        StaticJavaParser.getConfiguration().setLexicalPreservationEnabled(true);
        StaticJavaParser.getConfiguration().setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_11);

        Files.walk(sourceRoot)
            .filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
            .sorted(Comparator.naturalOrder())
            .forEach(Main::rewriteFile);
    }

    private static void rewriteFile(Path path) {
        try {
            CompilationUnit compilationUnit = StaticJavaParser.parse(path);
            LexicalPreservingPrinter.setup(compilationUnit);

            compilationUnit.findAll(MethodCallExpr.class).stream()
                .filter(Main::isOldLoggerManagerCall)
                .forEach(Main::castContainerScope);

            Files.writeString(path, LexicalPreservingPrinter.print(compilationUnit), StandardCharsets.UTF_8);
        } catch (Exception ignored) {
            // Skip files that cannot be parsed; the rule is meant to be broadly applicable.
        }
    }

    private static boolean isOldLoggerManagerCall(MethodCallExpr call) {
        return call.getArguments().isEmpty() && call.getNameAsString().equals("getLoggerManager");
    }

    private static void castContainerScope(MethodCallExpr call) {
        call.getScope().ifPresent(scope -> {
            if (!(scope instanceof CastExpr)) {
                Expression castScope = scope.clone();
                call.setScope(new CastExpr(
                    StaticJavaParser.parseType("org.codehaus.plexus.MutablePlexusContainer"),
                    castScope));
            }
        });
    }
}
