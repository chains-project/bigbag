package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
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
import java.util.Optional;
import java.util.stream.Stream;

public class Main {

    private static final String OLD_TYPE = "com.jcabi.aspects.Tv";

    private static final Map<String, Integer> SMALL_NUMBERS = new HashMap<>();

    static {
        SMALL_NUMBERS.put("ZERO", 0);
        SMALL_NUMBERS.put("ONE", 1);
        SMALL_NUMBERS.put("TWO", 2);
        SMALL_NUMBERS.put("THREE", 3);
        SMALL_NUMBERS.put("FOUR", 4);
        SMALL_NUMBERS.put("FIVE", 5);
        SMALL_NUMBERS.put("SIX", 6);
        SMALL_NUMBERS.put("SEVEN", 7);
        SMALL_NUMBERS.put("EIGHT", 8);
        SMALL_NUMBERS.put("NINE", 9);
        SMALL_NUMBERS.put("TEN", 10);
        SMALL_NUMBERS.put("ELEVEN", 11);
        SMALL_NUMBERS.put("TWELVE", 12);
        SMALL_NUMBERS.put("THIRTEEN", 13);
        SMALL_NUMBERS.put("FOURTEEN", 14);
        SMALL_NUMBERS.put("FIFTEEN", 15);
        SMALL_NUMBERS.put("SIXTEEN", 16);
        SMALL_NUMBERS.put("SEVENTEEN", 17);
        SMALL_NUMBERS.put("EIGHTEEN", 18);
        SMALL_NUMBERS.put("NINETEEN", 19);
        SMALL_NUMBERS.put("TWENTY", 20);
        SMALL_NUMBERS.put("THIRTY", 30);
        SMALL_NUMBERS.put("FORTY", 40);
        SMALL_NUMBERS.put("FIFTY", 50);
        SMALL_NUMBERS.put("SIXTY", 60);
        SMALL_NUMBERS.put("SEVENTY", 70);
        SMALL_NUMBERS.put("EIGHTY", 80);
        SMALL_NUMBERS.put("NINETY", 90);
    }

    public static void main(final String[] args) {
        final Path root = Paths.get(args.length > 0 ? args[0] : ".").toAbsolutePath().normalize();
        try (Stream<Path> files = Files.walk(root)) {
            files.filter(path -> path.toString().endsWith(".java"))
                .forEach(Main::transform);
        } catch (final IOException ex) {
            throw new IllegalStateException("Failed to walk source tree at " + root, ex);
        }
    }

    private static void transform(final Path file) {
        try {
            final CompilationUnit cu = StaticJavaParser.parse(file, StandardCharsets.UTF_8);
            LexicalPreservingPrinter.setup(cu);
            final TvRewriter rewriter = new TvRewriter();
            rewriter.visit(cu, null);
            final boolean changed = rewriter.changed;
            if (changed) {
                Files.write(file, LexicalPreservingPrinter.print(cu).getBytes(StandardCharsets.UTF_8));
            }
        } catch (final IOException ex) {
            throw new IllegalStateException("Failed to transform " + file, ex);
        }
    }

    private static final class TvRewriter extends ModifierVisitor<Void> {

        private boolean changed;

        @Override
        public Visitable visit(final FieldAccessExpr expr, final Void arg) {
            final Visitable visited = super.visit(expr, arg);
            if (visited instanceof FieldAccessExpr) {
                final FieldAccessExpr access = (FieldAccessExpr) visited;
                final Optional<Integer> value = constantValue(access);
                if (value.isPresent()) {
                    this.changed = true;
                    return new IntegerLiteralExpr(Integer.toString(value.get()));
                }
            }
            return visited;
        }

        @Override
        public Visitable visit(final NameExpr expr, final Void arg) {
            final Visitable visited = super.visit(expr, arg);
            if (visited instanceof NameExpr) {
                final Optional<Integer> value = constantValue(((NameExpr) visited).getNameAsString());
                if (value.isPresent()) {
                    this.changed = true;
                    return new IntegerLiteralExpr(Integer.toString(value.get()));
                }
            }
            return visited;
        }

        @Override
        public Node visit(final ImportDeclaration expr, final Void arg) {
            if (expr.getNameAsString().equals(OLD_TYPE)
                || (expr.isStatic() && expr.getNameAsString().startsWith(OLD_TYPE + "."))) {
                this.changed = true;
                return null;
            }
            return super.visit(expr, arg);
        }

        private static Optional<Integer> constantValue(final FieldAccessExpr expr) {
            final String scope = expr.getScope().toString();
            if ("Tv".equals(scope) || OLD_TYPE.equals(scope)) {
                return constantValue(expr.getNameAsString());
            }
            return Optional.empty();
        }

        private static Optional<Integer> constantValue(final String name) {
            if (SMALL_NUMBERS.containsKey(name)) {
                return Optional.of(SMALL_NUMBERS.get(name));
            }
            if ("HUNDRED".equals(name)) {
                return Optional.of(100);
            }
            if ("THOUSAND".equals(name)) {
                return Optional.of(1_000);
            }
            if ("MILLION".equals(name)) {
                return Optional.of(1_000_000);
            }
            if ("BILLION".equals(name)) {
                return Optional.of(1_000_000_000);
            }
            if (name.contains("_")) {
                int total = 0;
                int current = 0;
                boolean seen = false;
                for (final String part : name.split("_")) {
                    final Optional<Integer> simple = constantValue(part);
                    if (simple.isPresent()) {
                        current += simple.get();
                        seen = true;
                        continue;
                    }
                    if ("HUNDRED".equals(part)) {
                        current = Math.max(1, current) * 100;
                        seen = true;
                        continue;
                    }
                    if ("THOUSAND".equals(part)) {
                        total += Math.max(1, current) * 1_000;
                        current = 0;
                        seen = true;
                        continue;
                    }
                    if ("MILLION".equals(part)) {
                        total += Math.max(1, current) * 1_000_000;
                        current = 0;
                        seen = true;
                        continue;
                    }
                    if ("BILLION".equals(part)) {
                        total += Math.max(1, current) * 1_000_000_000;
                        current = 0;
                        seen = true;
                        continue;
                    }
                    return Optional.empty();
                }
                return seen ? Optional.of(total + current) : Optional.empty();
            }
            return Optional.empty();
        }
    }
}
