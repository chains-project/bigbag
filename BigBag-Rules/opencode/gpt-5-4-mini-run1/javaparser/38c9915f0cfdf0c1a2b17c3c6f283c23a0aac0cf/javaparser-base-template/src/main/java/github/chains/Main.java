package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.BooleanLiteralExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.expr.IntegerLiteralExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.LambdaExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.SimpleName;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.stmt.ExplicitConstructorInvocationStmt;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public final class Main {
    private static final Map<String, String> REWRITES = new LinkedHashMap<>();

    static {
        REWRITES.put("org.cactoos.iterable.LengthOf", "org.cactoos.scalar.LengthOf");
        REWRITES.put("org.cactoos.text.RandomText", "org.cactoos.text.Randomized");
        REWRITES.put("org.cactoos.collection.Filtered", "org.cactoos.iterable.Filtered");
        REWRITES.put("org.cactoos.scalar.CheckedScalar", "org.cactoos.scalar.Checked");
        REWRITES.put("org.cactoos.scalar.UncheckedScalar", "org.cactoos.scalar.Unchecked");
        REWRITES.put("org.cactoos.text.SplitText", "org.cactoos.text.Split");
        REWRITES.put("org.cactoos.collection.CollectionOf", "org.cactoos.set.SetOf");
        REWRITES.put("org.cactoos.scalar.IoCheckedScalar", "org.cactoos.scalar.IoChecked");
        REWRITES.put("org.cactoos.scalar.SolidScalar", "org.cactoos.scalar.Solid");
        REWRITES.put("org.cactoos.text.JoinedText", "org.cactoos.text.Joined");
        REWRITES.put("org.cactoos.scalar.StickyScalar", "org.cactoos.scalar.Sticky");
        REWRITES.put("org.cactoos.text.TrimmedText", "org.cactoos.text.Trimmed");
    }

    private Main() {
        // Utility class.
    }

    public static void main(final String[] args) {
        final Path root = Paths.get(args.length > 0 ? args[0] : ".");
        try (Stream<Path> paths = Files.walk(root)) {
            paths.filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                .forEach(Main::rewrite);
        } catch (final IOException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static void rewrite(final Path path) {
        try {
            final String original = Files.readString(path);
            final String preprocessed = rewriteText(original);
            final CompilationUnit cu = StaticJavaParser.parse(preprocessed);
            rewriteSuperLambdas(cu);
            rewriteLengthCalls(cu);
            rewriteFilteredIsEmpty(cu);
            rewriteIterableOfConstructors(cu);
            rewriteImports(cu);
            rewriteTypes(cu);
            final String updated = rewriteText(cu.toString());
            if (!Objects.equals(original, updated)) {
                Files.writeString(path, updated, StandardCharsets.UTF_8);
            }
        } catch (final IOException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static void rewriteImports(final CompilationUnit cu) {
        REWRITES.forEach((oldName, newName) -> {
            cu.getImports().stream()
                .filter(imp -> imp.getNameAsString().equals(oldName))
                .findFirst()
                .ifPresent(imp -> {
                    cu.remove(imp);
                    if (cu.getImports().stream().noneMatch(cur -> cur.getNameAsString().equals(newName))) {
                        cu.addImport(newName);
                    }
                });
        });
    }

    private static void rewriteTypes(final CompilationUnit cu) {
        cu.findAll(SimpleName.class).forEach(Main::rewriteName);
        cu.findAll(ClassOrInterfaceType.class).forEach(type -> rewriteType(type));
        cu.findAll(ObjectCreationExpr.class).forEach(expr -> rewriteType(expr.getType()));
    }

    private static void rewriteSuperLambdas(final CompilationUnit cu) {
        cu.findAll(ExplicitConstructorInvocationStmt.class).forEach(stmt -> {
            stmt.getArguments().stream()
                .filter(LambdaExpr.class::isInstance)
                .map(LambdaExpr.class::cast)
                .filter(lambda -> lambda.getParameters().isEmpty() && lambda.getBody() instanceof ExpressionStmt)
                .findFirst()
                .ifPresent(lambda -> {
                    final Expression body = ((ExpressionStmt) lambda.getBody()).getExpression();
                    stmt.getArguments().replace(lambda, body);
                });
        });
    }

    private static void rewriteLengthCalls(final CompilationUnit cu) {
        cu.findAll(MethodCallExpr.class).forEach(call -> {
            if (!"intValue".equals(call.getNameAsString()) || call.getScope().isEmpty()) {
                return;
            }
            final Expression scope = call.getScope().get();
            if (scope.isObjectCreationExpr()) {
                final ObjectCreationExpr creation = scope.asObjectCreationExpr();
                if (simpleName(creation.getType().getNameAsString()).equals("LengthOf")) {
                    call.setScope(new MethodCallExpr(scope, "value"));
                }
            }
        });
    }

    private static void rewriteFilteredIsEmpty(final CompilationUnit cu) {
        cu.findAll(MethodCallExpr.class).forEach(call -> {
            if (!"isEmpty".equals(call.getNameAsString()) || call.getScope().isEmpty()) {
                return;
            }
            final Expression scope = call.getScope().get();
            if (scope.isObjectCreationExpr() && simpleName(scope.asObjectCreationExpr().getType().getNameAsString()).equals("Filtered")) {
                final ObjectCreationExpr length = new ObjectCreationExpr();
                length.setType(StaticJavaParser.parseClassOrInterfaceType("org.cactoos.scalar.LengthOf"));
                length.addArgument(scope.clone());
                final MethodCallExpr value = new MethodCallExpr(length, "value");
                final MethodCallExpr intValue = new MethodCallExpr(value, "intValue");
                final BinaryExpr replacement = new BinaryExpr(intValue, new IntegerLiteralExpr("0"), BinaryExpr.Operator.EQUALS);
                call.replace(replacement);
            }
        });
    }

    private static void rewriteIterableOfConstructors(final CompilationUnit cu) {
        cu.findAll(ObjectCreationExpr.class).forEach(expr -> {
            if (!simpleName(expr.getType().getNameAsString()).equals("IterableOf") || expr.getArguments().size() != 1) {
                return;
            }
            final Expression arg = expr.getArgument(0);
            if (!(arg.isMethodCallExpr() && arg.asMethodCallExpr().getNameAsString().equals("iterator"))) {
                expr.setArgument(0, new MethodCallExpr(arg.clone(), "iterator"));
            }
        });
    }

    private static void rewriteName(final SimpleName name) {
        REWRITES.forEach((oldName, newName) -> {
            if (name.asString().equals(simpleName(oldName))) {
                name.setIdentifier(simpleName(newName));
            }
        });
    }

    private static void rewriteType(final ClassOrInterfaceType type) {
        REWRITES.forEach((oldName, newName) -> {
            if (type.getNameAsString().equals(simpleName(oldName))) {
                type.setName(simpleName(newName));
            }
        });
    }

    private static String simpleName(final String fqcn) {
        return fqcn.substring(fqcn.lastIndexOf('.') + 1);
    }

    private static String rewriteText(final String text) {
        String result = text;
        for (final Map.Entry<String, String> entry : REWRITES.entrySet()) {
            final String oldName = simpleName(entry.getKey());
            final String newName = simpleName(entry.getValue());
            result = Pattern.compile("\\b" + Pattern.quote(oldName) + "\\b").matcher(result)
                .replaceAll(newName);
        }
        return result;
    }
}
