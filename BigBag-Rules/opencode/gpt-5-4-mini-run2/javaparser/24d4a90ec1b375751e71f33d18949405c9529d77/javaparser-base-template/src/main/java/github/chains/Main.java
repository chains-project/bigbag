package github.chains;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.IntegerLiteralExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import com.github.javaparser.StaticJavaParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

public final class Main {

    private static final String OLD_TYPE = "com.jcabi.aspects.Tv";

    private static final Map<String, String> CONSTANTS = new HashMap<>();

    static {
        CONSTANTS.put("FIVE", "5");
        CONSTANTS.put("HUNDRED", "100");
        CONSTANTS.put("THOUSAND", "1000");
    }

    private Main() {
        // utility class
    }

    public static void main(final String[] args) throws IOException {
        final Path root = args.length > 0 ? Paths.get(args[0]) : Paths.get(".");
        try (Stream<Path> paths = Files.walk(root)) {
            paths.filter(path -> path.toString().endsWith(".java"))
                .forEach(Main::transformSafely);
        }
    }

    private static void transformSafely(final Path file) {
        try {
            transform(file);
        } catch (final IOException ex) {
            throw new IllegalStateException("Failed to transform " + file, ex);
        }
    }

    private static void transform(final Path file) throws IOException {
        final String before = Files.readString(file, StandardCharsets.UTF_8);
        final CompilationUnit cu = StaticJavaParser.parse(before);
        final Set<String> staticImports = new HashSet<>();
        boolean removePlainImport = false;
        for (final ImportDeclaration imp : cu.getImports()) {
            if (imp.getNameAsString().equals(OLD_TYPE)) {
                removePlainImport = true;
            }
            if (imp.isStatic() && imp.getNameAsString().startsWith(OLD_TYPE + ".")) {
                staticImports.add(imp.getNameAsString().substring(OLD_TYPE.length() + 1));
            }
        }
        if (!removePlainImport && staticImports.isEmpty()) {
            return;
        }
        cu.getImports().removeIf(imp -> imp.getNameAsString().equals(OLD_TYPE)
            || (imp.isStatic() && imp.getNameAsString().startsWith(OLD_TYPE + ".")));
        cu.accept(new TvVisitor(staticImports), null);
        final String after = cu.toString();
        if (!before.equals(after)) {
            Files.writeString(file, after, StandardCharsets.UTF_8);
        }
    }

    private static final class TvVisitor extends ModifierVisitor<Void> {

        private final Set<String> staticImports;

        private TvVisitor(final Set<String> staticImports) {
            this.staticImports = staticImports;
        }

        @Override
        public Visitable visit(final FieldAccessExpr expr, final Void arg) {
            if (isTvConstant(expr)) {
                return new IntegerLiteralExpr(CONSTANTS.get(expr.getNameAsString()));
            }
            return super.visit(expr, arg);
        }

        @Override
        public Visitable visit(final NameExpr expr, final Void arg) {
            if (this.staticImports.contains(expr.getNameAsString())
                && CONSTANTS.containsKey(expr.getNameAsString())) {
                return new IntegerLiteralExpr(CONSTANTS.get(expr.getNameAsString()));
            }
            return super.visit(expr, arg);
        }

        private static boolean isTvConstant(final FieldAccessExpr expr) {
            if (!CONSTANTS.containsKey(expr.getNameAsString())) {
                return false;
            }
            final Node scope = expr.getScope();
            final String text = scope.toString();
            return "Tv".equals(text) || OLD_TYPE.equals(text);
        }
    }
}
