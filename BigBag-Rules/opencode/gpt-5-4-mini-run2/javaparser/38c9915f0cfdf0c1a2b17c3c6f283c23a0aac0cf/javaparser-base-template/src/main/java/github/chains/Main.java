package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.LambdaExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

public final class Main {
    private static final Map<String, String> IMPORTS = Map.ofEntries(
        Map.entry("org.cactoos.collection.CollectionOf", "org.cactoos.list.ListOf"),
        Map.entry("org.cactoos.collection.Filtered", "org.cactoos.iterable.Filtered"),
        Map.entry("org.cactoos.iterable.LengthOf", "org.cactoos.scalar.LengthOf"),
        Map.entry("org.cactoos.text.RandomText", "org.cactoos.text.Randomized"),
        Map.entry("org.cactoos.scalar.CheckedScalar", "org.cactoos.scalar.Checked"),
        Map.entry("org.cactoos.scalar.IoCheckedScalar", "org.cactoos.scalar.IoChecked"),
        Map.entry("org.cactoos.scalar.SolidScalar", "org.cactoos.scalar.Solid"),
        Map.entry("org.cactoos.scalar.StickyScalar", "org.cactoos.scalar.Sticky"),
        Map.entry("org.cactoos.scalar.UncheckedScalar", "org.cactoos.scalar.Unchecked"),
        Map.entry("org.cactoos.text.SplitText", "org.cactoos.text.Split"),
        Map.entry("org.cactoos.text.JoinedText", "org.cactoos.text.Joined"),
        Map.entry("org.cactoos.text.TrimmedText", "org.cactoos.text.Trimmed")
    );

    private static final Map<String, String> SIMPLE = Map.ofEntries(
        Map.entry("CollectionOf", "ListOf"),
        Map.entry("Filtered", "Filtered"),
        Map.entry("LengthOf", "LengthOf"),
        Map.entry("RandomText", "Randomized"),
        Map.entry("CheckedScalar", "Checked"),
        Map.entry("IoCheckedScalar", "IoChecked"),
        Map.entry("SolidScalar", "Solid"),
        Map.entry("StickyScalar", "Sticky"),
        Map.entry("UncheckedScalar", "Unchecked"),
        Map.entry("SplitText", "Split"),
        Map.entry("JoinedText", "Joined"),
        Map.entry("TrimmedText", "Trimmed")
    );

    private Main() {
        // utility class
    }

    public static void main(final String[] args) throws IOException {
        final Path root = Paths.get(args.length == 0 ? "." : args[0]);
        try (Stream<Path> files = Files.walk(root)) {
            files.filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                .forEach(Main::rewriteSafely);
        }
    }

    private static void rewriteSafely(final Path file) {
        try {
            rewrite(file);
        } catch (final IOException ex) {
            throw new IllegalStateException("Failed to rewrite " + file, ex);
        }
    }

    private static void rewrite(final Path file) throws IOException {
        final CompilationUnit unit = StaticJavaParser.parse(Files.readString(file, StandardCharsets.UTF_8));
        unit.getImports().forEach(imp -> rewriteImport(imp));
        unit.findAll(ObjectCreationExpr.class).forEach(Main::rewriteCreation);
        unit.findAll(MethodCallExpr.class).forEach(Main::rewriteMethodCall);
        Files.writeString(file, unit.toString(), StandardCharsets.UTF_8);
    }

    private static void rewriteImport(final ImportDeclaration imp) {
        if (imp.isAsterisk() || imp.isStatic()) {
            return;
        }
        final String old = imp.getNameAsString();
        final String replacement = IMPORTS.get(old);
        if (replacement != null) {
            imp.setName(replacement);
        }
    }

    private static void rewriteCreation(final ObjectCreationExpr expr) {
        final String name = expr.getType().getNameAsString();
        if (SIMPLE.containsKey(name)) {
            expr.getType().setName(SIMPLE.get(name));
            expr.getTypeArguments().ifPresent(args -> args.clear());
        }
        if (name.equals("Skipped") && expr.getArguments().size() == 2) {
            final Expression first = expr.getArgument(0);
            final Expression second = expr.getArgument(1);
            expr.getArguments().set(0, second);
            expr.getArguments().set(1, first);
        }
        if (expr.getArguments().size() == 1) {
            final Expression arg = expr.getArgument(0);
            if (arg.isLambdaExpr()) {
                final LambdaExpr lambda = arg.asLambdaExpr();
                if (lambda.getBody().isExpressionStmt()) {
                    expr.setArgument(0, lambda.getBody().asExpressionStmt().getExpression());
                }
            }
        }
    }

    private static void rewriteMethodCall(final MethodCallExpr call) {
        if (call.getNameAsString().equals("intValue")
            && call.getScope().filter(ObjectCreationExpr.class::isInstance).isPresent()) {
            final ObjectCreationExpr scope = call.getScope().get().asObjectCreationExpr();
            if (scope.getType().getNameAsString().equals("LengthOf")) {
                call.setName("value");
            }
        }
        if (call.getNameAsString().equals("isEmpty") && call.getScope().isPresent()) {
            final Optional<Node> scope = call.getScope().map(Node.class::cast);
            if (scope.isPresent() && scope.get() instanceof ObjectCreationExpr) {
                final ObjectCreationExpr expr = (ObjectCreationExpr) scope.get();
                if (expr.getType().getNameAsString().equals("Filtered")) {
                    final ObjectCreationExpr list = StaticJavaParser.parseExpression("new org.cactoos.list.ListOf<>(" + expr + ")").asObjectCreationExpr();
                    call.setScope(list);
                }
            }
        }
    }
}
