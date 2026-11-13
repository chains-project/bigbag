package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.IntegerLiteralExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.visitor.Visitable;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

public class Main {

    private static final Map<String, Integer> TV_VALUES = new HashMap<>();

    static {
        TV_VALUES.put("ZERO", 0);
        TV_VALUES.put("ONE", 1);
        TV_VALUES.put("TWO", 2);
        TV_VALUES.put("THREE", 3);
        TV_VALUES.put("FOUR", 4);
        TV_VALUES.put("FIVE", 5);
        TV_VALUES.put("SIX", 6);
        TV_VALUES.put("SEVEN", 7);
        TV_VALUES.put("EIGHT", 8);
        TV_VALUES.put("NINE", 9);
        TV_VALUES.put("TEN", 10);
        TV_VALUES.put("ELEVEN", 11);
        TV_VALUES.put("TWELVE", 12);
        TV_VALUES.put("THIRTEEN", 13);
        TV_VALUES.put("FOURTEEN", 14);
        TV_VALUES.put("FIFTEEN", 15);
        TV_VALUES.put("SIXTEEN", 16);
        TV_VALUES.put("SEVENTEEN", 17);
        TV_VALUES.put("EIGHTEEN", 18);
        TV_VALUES.put("NINETEEN", 19);
        TV_VALUES.put("TWENTY", 20);
        TV_VALUES.put("THIRTY", 30);
        TV_VALUES.put("FORTY", 40);
        TV_VALUES.put("FIFTY", 50);
        TV_VALUES.put("SIXTY", 60);
        TV_VALUES.put("SEVENTY", 70);
        TV_VALUES.put("EIGHTY", 80);
        TV_VALUES.put("NINETY", 90);
        TV_VALUES.put("HUNDRED", 100);
        TV_VALUES.put("THOUSAND", 1000);
        TV_VALUES.put("MILLION", 1000000);
        TV_VALUES.put("BILLION", 1000000000);
    }

    public static void main(String[] args) {
        final Path root = Paths.get(args.length > 0 ? args[0] : ".").toAbsolutePath().normalize();
        try (Stream<Path> paths = Files.walk(root)) {
            paths.filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                .forEach(Main::rewrite);
        } catch (final IOException ex) {
            throw new IllegalStateException("Failed to walk source tree: " + root, ex);
        }
    }

    private static void rewrite(final Path file) {
        try {
            final CompilationUnit unit = StaticJavaParser.parse(file);
            final boolean[] changed = {false};
            LexicalPreservingPrinter.setup(unit);
            unit.accept(new ModifierVisitor<Void>() {
                @Override
                public Visitable visit(final FieldAccessExpr expr, final Void arg) {
                    final Visitable visited = super.visit(expr, arg);
                    if (!(visited instanceof FieldAccessExpr)) {
                        return visited;
                    }
                    final FieldAccessExpr field = (FieldAccessExpr) visited;
                    final Integer value = tvValue(field);
                    if (value == null) {
                        return field;
                    }
                    changed[0] = true;
                    return new IntegerLiteralExpr(Integer.toString(value));
                }
            }, null);
            if (removeTvImports(unit)) {
                changed[0] = true;
            }
            if (changed[0]) {
                Files.write(file, LexicalPreservingPrinter.print(unit).getBytes(StandardCharsets.UTF_8));
            }
        } catch (final Exception ex) {
            throw new IllegalStateException("Failed to rewrite " + file, ex);
        }
    }

    private static boolean removeTvImports(final CompilationUnit unit) {
        return unit.getImports().removeIf(imp -> {
            final String name = imp.getNameAsString();
            return "com.jcabi.aspects.Tv".equals(name)
                || (imp.isAsterisk() && "com.jcabi.aspects".equals(name))
                || (imp.isStatic() && "com.jcabi.aspects.Tv".equals(name));
        });
    }

    private static Integer tvValue(final FieldAccessExpr expr) {
        if (!TV_VALUES.containsKey(expr.getNameAsString())) {
            return null;
        }
        final Expression scope = expr.getScope();
        if (scope instanceof NameExpr) {
            if ("Tv".equals(((NameExpr) scope).getNameAsString())) {
                return TV_VALUES.get(expr.getNameAsString());
            }
            return null;
        }
        if (scope instanceof FieldAccessExpr) {
            final String fqcn = scope.toString();
            if ("com.jcabi.aspects.Tv".equals(fqcn)) {
                return TV_VALUES.get(expr.getNameAsString());
            }
        }
        return null;
    }
}
