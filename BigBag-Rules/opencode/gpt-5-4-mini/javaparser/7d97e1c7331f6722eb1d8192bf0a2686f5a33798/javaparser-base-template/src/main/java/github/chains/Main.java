package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.IntegerLiteralExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

public final class Main {

    private static final String OLD_TYPE = "com.jcabi.aspects.Tv";

    private static final String OLD_SIMPLE = "Tv";

    private static final Map<String, Integer> CONSTANTS = constants();

    private Main() {
        // utility class
    }

    public static void main(final String[] args) throws IOException {
        final Path root = Paths.get(args.length > 0 ? args[0] : ".");
        try (Stream<Path> paths = Files.walk(root)) {
            paths.filter(path -> path.toString().endsWith(".java"))
                .forEach(Main::rewrite);
        }
    }

    private static void rewrite(final Path file) {
        try {
            final CompilationUnit cu = StaticJavaParser.parse(file);
            LexicalPreservingPrinter.setup(cu);
            final boolean[] changed = { false };
            cu.accept(new ModifierVisitor<Void>() {
                @Override
                public Visitable visit(final FieldAccessExpr expr, final Void arg) {
                    if (matchesOldApi(expr)) {
                        changed[0] = true;
                        return new IntegerLiteralExpr(
                            Integer.toString(CONSTANTS.get(expr.getNameAsString()))
                        );
                    }
                    return super.visit(expr, arg);
                }

                @Override
                public Visitable visit(final NameExpr expr, final Void arg) {
                    if (matchesStaticImport(expr, cu)) {
                        changed[0] = true;
                        return new IntegerLiteralExpr(
                            Integer.toString(CONSTANTS.get(expr.getNameAsString()))
                        );
                    }
                    return super.visit(expr, arg);
                }
            }, null);
            if (changed[0]) {
                cu.getImports().removeIf(Main::isObsoleteImport);
                Files.writeString(file, LexicalPreservingPrinter.print(cu));
            }
        } catch (final IOException ex) {
            throw new IllegalStateException(
                String.format("Failed to rewrite %s", file), ex
            );
        }
    }

    private static boolean matchesOldApi(final FieldAccessExpr expr) {
        return CONSTANTS.containsKey(expr.getNameAsString())
            && (OLD_TYPE.equals(qualifiedName(expr.getScope()))
            || OLD_SIMPLE.equals(expr.getScope().toString()));
    }

    private static boolean matchesStaticImport(final NameExpr expr,
        final CompilationUnit cu) {
        return CONSTANTS.containsKey(expr.getNameAsString())
            && cu.getImports().stream().anyMatch(
                imp -> imp.isStatic() && isTvStaticImport(imp, expr.getNameAsString())
            );
    }

    private static boolean isTvStaticImport(final ImportDeclaration imp,
        final String constant) {
        final String name = imp.getNameAsString();
        return name.equals(OLD_TYPE + '.' + constant)
            || (imp.isAsterisk() && name.equals(OLD_TYPE));
    }

    private static boolean isObsoleteImport(final ImportDeclaration imp) {
        final String name = imp.getNameAsString();
        return name.equals(OLD_TYPE) || name.equals(OLD_SIMPLE)
            || (imp.isStatic() && name.startsWith(OLD_TYPE + '.'));
    }

    private static String qualifiedName(final Expression expression) {
        if (expression.isNameExpr()) {
            return expression.asNameExpr().getNameAsString();
        }
        if (expression.isFieldAccessExpr()) {
            final FieldAccessExpr access = expression.asFieldAccessExpr();
            return String.format("%s.%s", qualifiedName(access.getScope()), access.getNameAsString());
        }
        return expression.toString();
    }

    private static Map<String, Integer> constants() {
        final Map<String, Integer> map = new HashMap<>(16);
        map.put("ZERO", 0);
        map.put("ONE", 1);
        map.put("TWO", 2);
        map.put("THREE", 3);
        map.put("FOUR", 4);
        map.put("FIVE", 5);
        map.put("SIX", 6);
        map.put("SEVEN", 7);
        map.put("EIGHT", 8);
        map.put("NINE", 9);
        map.put("TEN", 10);
        return map;
    }
}
