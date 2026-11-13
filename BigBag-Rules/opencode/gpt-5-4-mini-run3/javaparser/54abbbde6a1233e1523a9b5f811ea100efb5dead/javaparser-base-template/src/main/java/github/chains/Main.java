package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.IntegerLiteralExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.stream.Stream;

public class Main {

    private static final String REMOVED_TYPE = "com.jcabi.aspects.Tv";

    private static final Map<String, String> REPLACEMENTS = Map.of(
        "SEVEN", "7",
        "TEN", "10",
        "MILLION", "1000000"
    );

    public static void main(final String[] args) {
        final Path root = args.length > 0 ? Paths.get(args[0]) : Paths.get(".");
        if (!Files.isDirectory(root)) {
            throw new IllegalArgumentException(
                String.format("Source directory does not exist: %s", root)
            );
        }
        try (Stream<Path> paths = Files.walk(root)) {
            paths.filter(Main::isJavaFile).forEach(Main::rewrite);
        } catch (final IOException err) {
            throw new UncheckedIOException(err);
        }
    }

    private static boolean isJavaFile(final Path path) {
        return path.toString().endsWith(".java");
    }

    private static void rewrite(final Path path) {
        try {
            final CompilationUnit unit = StaticJavaParser.parse(path);
            LexicalPreservingPrinter.setup(unit);
            boolean changed = false;
            changed |= unit.getImports().removeIf(Main::isTvImport);
            changed |= replaceQualifiedReferences(unit);
            changed |= replaceUnqualifiedReferences(unit);
            if (changed) {
                Files.writeString(
                    path,
                    LexicalPreservingPrinter.print(unit),
                    StandardCharsets.UTF_8
                );
            }
        } catch (final IOException err) {
            throw new UncheckedIOException(err);
        }
    }

    private static boolean isTvImport(final ImportDeclaration imp) {
        return imp.getNameAsString().equals(REMOVED_TYPE);
    }

    private static boolean replaceQualifiedReferences(final CompilationUnit unit) {
        boolean changed = false;
        for (final FieldAccessExpr expr : unit.findAll(FieldAccessExpr.class)) {
            final String name = expr.getNameAsString();
            final String scope = expr.getScope().toString();
            if (REPLACEMENTS.containsKey(name)
                && (scope.equals("Tv") || scope.equals(REMOVED_TYPE))) {
                expr.replace(new IntegerLiteralExpr(REPLACEMENTS.get(name)));
                changed = true;
            }
        }
        return changed;
    }

    private static boolean replaceUnqualifiedReferences(final CompilationUnit unit) {
        final boolean removedImport = unit.getImports().stream()
            .map(ImportDeclaration::getNameAsString)
            .anyMatch(REMOVED_TYPE::equals);
        if (!removedImport) {
            return false;
        }
        boolean changed = false;
        for (final NameExpr expr : unit.findAll(NameExpr.class)) {
            final String name = expr.getNameAsString();
            if (REPLACEMENTS.containsKey(name)) {
                expr.replace(new IntegerLiteralExpr(REPLACEMENTS.get(name)));
                changed = true;
            }
        }
        return changed;
    }
}
