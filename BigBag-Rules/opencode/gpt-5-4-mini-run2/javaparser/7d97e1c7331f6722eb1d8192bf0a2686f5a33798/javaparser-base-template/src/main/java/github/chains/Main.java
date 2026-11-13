package github.chains;

import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.IntegerLiteralExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.visitor.Visitable;
import com.github.javaparser.ast.visitor.ModifierVisitor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

public final class Main {

    private static final String OLD_TYPE = "com.jcabi.aspects.Tv";

    private static final Map<String, Integer> CONSTANTS = constants();

    private Main() {
        // utility class
    }

    public static void main(final String[] args) throws IOException {
        if (args.length < 1 || args.length > 2) {
            throw new IllegalArgumentException(
                "Usage: Main <source-dir> [output-dir]"
            );
        }
        final Path input = Paths.get(args[0]).toAbsolutePath().normalize();
        final Path output = args.length == 2
            ? Paths.get(args[1]).toAbsolutePath().normalize()
            : input;
        final ParserConfiguration config = new ParserConfiguration();
        StaticJavaParser.setConfiguration(config);
        try (Stream<Path> paths = Files.walk(input)) {
            paths.filter(Main::isJavaFile).forEach(path -> {
                try {
                    rewrite(input, output, path);
                } catch (final IOException err) {
                    throw new IllegalStateException(err);
                }
            });
        }
    }

    private static void rewrite(final Path input, final Path output,
        final Path source) throws IOException {
        final CompilationUnit unit = StaticJavaParser.parse(
            source,
            StandardCharsets.UTF_8
        );
        final CompilationUnit changed = new TvConstantRewriter().rewrite(unit);
        removeObsoleteImports(changed);
        final Path target = output.resolve(input.relativize(source));
        Files.createDirectories(Objects.requireNonNull(target.getParent()));
        Files.writeString(target, changed.toString(), StandardCharsets.UTF_8);
    }

    private static void removeObsoleteImports(final CompilationUnit unit) {
        unit.getImports().removeIf(Main::isOldTypeImport);
    }

    private static boolean isOldTypeImport(final ImportDeclaration imp) {
        return imp.getNameAsString().equals(OLD_TYPE);
    }

    private static boolean isJavaFile(final Path path) {
        return Files.isRegularFile(path) && path.toString().endsWith(".java");
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
        values.put("THOUSAND", 1000);
        return values;
    }

    private static final class TvConstantRewriter extends ModifierVisitor<Void> {

        private CompilationUnit rewrite(final CompilationUnit unit) {
            return (CompilationUnit) this.visit(unit, null);
        }

        @Override
        public Visitable visit(
            final FieldAccessExpr expr,
            final Void arg
        ) {
            final Visitable visited = super.visit(
                expr,
                arg
            );
            if (!(visited instanceof FieldAccessExpr)) {
                return visited;
            }
            final FieldAccessExpr access = (FieldAccessExpr) visited;
            if (access.getScope().isNameExpr()
                && access.getScope().asNameExpr().getNameAsString().equals("Tv")) {
                final Integer value = CONSTANTS.get(
                    access.getNameAsString().toUpperCase(Locale.ENGLISH)
                );
                if (value != null) {
                    return new IntegerLiteralExpr(Integer.toString(value));
                }
            }
            return access;
        }

        @Override
        public Visitable visit(final NameExpr expr, final Void arg) {
            return super.visit(expr, arg);
        }
    }
}
