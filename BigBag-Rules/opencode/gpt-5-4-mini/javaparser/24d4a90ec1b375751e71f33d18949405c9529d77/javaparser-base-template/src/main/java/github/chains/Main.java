package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.IntegerLiteralExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public final class Main {

    private static final String OLD_TYPE = "com.jcabi.aspects.Tv";

    private static final Map<String, Integer> CONSTANTS = constants();

    private Main() {
        // utility class
    }

    public static void main(final String[] args) {
        if (args.length != 1) {
            throw new IllegalArgumentException("Usage: Main <source-root>");
        }
        final Path root = Paths.get(args[0]);
        try (Stream<Path> files = Files.walk(root)) {
            files.filter(path -> path.toString().endsWith(".java"))
                .forEach(Main::rewrite);
        } catch (final IOException err) {
            throw new IllegalStateException("Failed to scan " + root, err);
        }
    }

    private static void rewrite(final Path file) {
        try {
            final CompilationUnit cu = StaticJavaParser.parse(file, StandardCharsets.UTF_8);
            LexicalPreservingPrinter.setup(cu);
            if (transform(cu)) {
                Files.writeString(file, LexicalPreservingPrinter.print(cu), StandardCharsets.UTF_8);
            }
        } catch (final IOException err) {
            throw new IllegalStateException("Failed to transform " + file, err);
        }
    }

    private static boolean transform(final CompilationUnit cu) {
        boolean changed = false;
        changed |= removeTvImports(cu);
        changed |= replaceQualifiedConstants(cu);
        changed |= replaceStaticConstants(cu);
        return changed;
    }

    private static boolean removeTvImports(final CompilationUnit cu) {
        final List<ImportDeclaration> imports = cu.getImports();
        return imports.removeIf(
            imp -> {
                final String name = imp.getNameAsString();
                return name.equals(OLD_TYPE) || name.startsWith(OLD_TYPE + ".");
            }
        );
    }

    private static boolean replaceQualifiedConstants(final CompilationUnit cu) {
        boolean changed = false;
        for (final FieldAccessExpr expr : cu.findAll(FieldAccessExpr.class)) {
            if (isTvConstantAccess(expr.getScope(), expr.getNameAsString())) {
                expr.replace(new IntegerLiteralExpr(CONSTANTS.get(expr.getNameAsString())));
                changed = true;
            }
        }
        return changed;
    }

    private static boolean replaceStaticConstants(final CompilationUnit cu) {
        if (!hasTvStaticImport(cu)) {
            return false;
        }
        boolean changed = false;
        for (final NameExpr expr : cu.findAll(NameExpr.class)) {
            final Integer value = CONSTANTS.get(expr.getNameAsString());
            if (value != null) {
                expr.replace(new IntegerLiteralExpr(value));
                changed = true;
            }
        }
        return changed;
    }

    private static boolean hasTvStaticImport(final CompilationUnit cu) {
        for (final ImportDeclaration imp : cu.getImports()) {
            if (imp.isStatic()) {
                final String name = imp.getNameAsString();
                if (name.equals(OLD_TYPE) || name.startsWith(OLD_TYPE + ".")) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isTvConstantAccess(final Expression scope, final String name) {
        return CONSTANTS.containsKey(name) && rightmostName(scope).equals("Tv");
    }

    private static String rightmostName(final Expression expr) {
        if (expr instanceof NameExpr) {
            return ((NameExpr) expr).getNameAsString();
        }
        if (expr instanceof FieldAccessExpr) {
            return ((FieldAccessExpr) expr).getNameAsString();
        }
        return "";
    }

    private static Map<String, Integer> constants() {
        final Map<String, Integer> values = new HashMap<>();
        values.put("ZERO", 0);
        values.put("ONE", 1);
        values.put("TWO", 2);
        values.put("THREE", 3);
        values.put("FOUR", 4);
        values.put("FIVE", 5);
        values.put("SIX", 6);
        values.put("SEVEN", 7);
        values.put("EIGHT", 8);
        values.put("NINE", 9);
        values.put("TEN", 10);
        values.put("ELEVEN", 11);
        values.put("TWELVE", 12);
        values.put("THIRTEEN", 13);
        values.put("FOURTEEN", 14);
        values.put("FIFTEEN", 15);
        values.put("SIXTEEN", 16);
        values.put("SEVENTEEN", 17);
        values.put("EIGHTEEN", 18);
        values.put("NINETEEN", 19);
        values.put("TWENTY", 20);
        values.put("THIRTY", 30);
        values.put("FORTY", 40);
        values.put("FIFTY", 50);
        values.put("SIXTY", 60);
        values.put("SEVENTY", 70);
        values.put("EIGHTY", 80);
        values.put("NINETY", 90);
        values.put("HUNDRED", 100);
        values.put("THOUSAND", 1_000);
        values.put("MILLION", 1_000_000);
        return values;
    }
}
