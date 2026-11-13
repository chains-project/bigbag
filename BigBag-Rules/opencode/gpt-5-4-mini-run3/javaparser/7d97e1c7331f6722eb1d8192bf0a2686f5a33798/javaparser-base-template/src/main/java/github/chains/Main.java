package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.IntegerLiteralExpr;
import com.github.javaparser.ast.expr.NameExpr;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

public class Main {

    private static final String TV_TYPE = "com.jcabi.aspects.Tv";

    private static final Map<String, Integer> TV_CONSTANTS = new HashMap<>();

    static {
        TV_CONSTANTS.put("ZERO", 0);
        TV_CONSTANTS.put("ONE", 1);
        TV_CONSTANTS.put("TWO", 2);
        TV_CONSTANTS.put("THREE", 3);
        TV_CONSTANTS.put("FOUR", 4);
        TV_CONSTANTS.put("FIVE", 5);
        TV_CONSTANTS.put("SIX", 6);
        TV_CONSTANTS.put("SEVEN", 7);
        TV_CONSTANTS.put("EIGHT", 8);
        TV_CONSTANTS.put("NINE", 9);
        TV_CONSTANTS.put("TEN", 10);
        TV_CONSTANTS.put("ELEVEN", 11);
        TV_CONSTANTS.put("TWELVE", 12);
        TV_CONSTANTS.put("HUNDRED", 100);
        TV_CONSTANTS.put("THOUSAND", 1000);
    }

    public static void main(final String[] args) throws IOException {
        if (args.length != 1) {
            throw new IllegalArgumentException("Expected one argument: source root");
        }
        final Path root = Paths.get(args[0]);
        try (Stream<Path> paths = Files.walk(root)) {
            paths.filter(path -> path.toString().endsWith(".java"))
                .forEach(Main::transform);
        }
    }

    private static void transform(final Path path) {
        try {
            final CompilationUnit unit = StaticJavaParser.parse(path, StandardCharsets.UTF_8);
            boolean changed = removeImports(unit);
            changed |= replaceTvConstants(unit);
            if (changed) {
                Files.write(path, unit.toString().getBytes(StandardCharsets.UTF_8));
            }
        } catch (final Exception ignored) {
            // Leave files untouched when they cannot be parsed safely.
        }
    }

    private static boolean removeImports(final CompilationUnit unit) {
        boolean changed = false;
        changed |= unit.getImports().removeIf(Main::isTvImport);
        return changed;
    }

    private static boolean isTvImport(final ImportDeclaration importDeclaration) {
        final String name = importDeclaration.getNameAsString();
        return name.equals(TV_TYPE) || name.startsWith(TV_TYPE + ".");
    }

    private static boolean replaceTvConstants(final CompilationUnit unit) {
        boolean changed = false;
        final String alias = unit.getImports().stream()
            .map(ImportDeclaration::getNameAsString)
            .filter(name -> name.equals(TV_TYPE) || name.endsWith(".Tv"))
            .map(name -> name.substring(name.lastIndexOf('.') + 1))
            .findFirst()
            .orElse("Tv");
        for (final FieldAccessExpr expr : unit.findAll(FieldAccessExpr.class)) {
            changed |= replaceIfTvConstant(expr, expr.getScope().toString(), expr.getNameAsString(), alias);
        }
        if (hasTvStaticImport(unit)) {
            for (final NameExpr expr : unit.findAll(NameExpr.class)) {
                changed |= replaceIfStaticImportedTvConstant(unit, expr);
            }
        }
        return changed;
    }

    private static boolean replaceIfTvConstant(final Expression expression,
        final String scope, final String name, final String alias) {
        if ((scope.equals(alias) || scope.equals(TV_TYPE)) && TV_CONSTANTS.containsKey(name)) {
            expression.replace(new IntegerLiteralExpr(TV_CONSTANTS.get(name).toString()));
            return true;
        }
        return false;
    }

    private static boolean replaceIfStaticImportedTvConstant(final CompilationUnit unit, final NameExpr expr) {
        final String name = expr.getNameAsString();
        if (TV_CONSTANTS.containsKey(name) && isTvConstantStaticImport(unit, name)) {
            expr.replace(new IntegerLiteralExpr(TV_CONSTANTS.get(name).toString()));
            return true;
        }
        return false;
    }

    private static boolean hasTvStaticImport(final CompilationUnit unit) {
        return unit.getImports().stream().anyMatch(ImportDeclaration::isStatic)
            && unit.getImports().stream().anyMatch(Main::isTvImport);
    }

    private static boolean isTvConstantStaticImport(final CompilationUnit unit, final String constant) {
        for (final ImportDeclaration declaration : unit.getImports()) {
            if (declaration.isStatic()) {
                final String name = declaration.getNameAsString();
                if (name.equals(TV_TYPE + "." + constant) ||
                    (declaration.isAsterisk() && name.equals(TV_TYPE))) {
                    return true;
                }
            }
        }
        return false;
    }

}
