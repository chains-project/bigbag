package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.stream.Stream;

public final class Main {

    private static final String OLD_AUTH = "com.artipie.http.auth.Authentication";

    private static final String OLD_USER = OLD_AUTH + ".User";

    private static final String OLD_SINGLE = OLD_AUTH + ".Single";

    private static final String NEW_USER = "com.artipie.http.auth.AuthUser";

    private Main() {
    }

    public static void main(final String[] args) {
        if (args.length != 1) {
            throw new IllegalArgumentException("Usage: Main <source-dir>");
        }
        final Path root = Paths.get(args[0]).toAbsolutePath().normalize();
        try (Stream<Path> paths = Files.walk(root)) {
            paths.filter(path -> path.toString().endsWith(".java")).forEach(Main::transform);
        } catch (final IOException err) {
            throw new IllegalStateException("Failed to walk sources under " + root, err);
        }
    }

    private static void transform(final Path file) {
        try {
            final CompilationUnit cu = StaticJavaParser.parse(file, StandardCharsets.UTF_8);
            cu.getImports().removeIf(imp -> imp.getNameAsString().equals(OLD_USER));
            cu.getImports().removeIf(imp -> imp.getNameAsString().equals(OLD_AUTH));
            cu.findAll(ObjectCreationExpr.class).forEach(Main::rewriteCreation);
            cu.findAll(MethodCallExpr.class).forEach(Main::rewriteMethodCall);
            Files.writeString(file, cu.toString(), StandardCharsets.UTF_8);
        } catch (final IOException err) {
            throw new IllegalStateException("Failed to transform " + file, err);
        }
    }

    private static void rewriteCreation(final ObjectCreationExpr creation) {
        final String type = creation.getType().getNameAsString();
        if (OLD_USER.endsWith("." + type) || OLD_USER.equals(creation.getType().asString())) {
            creation.setType(StaticJavaParser.parseClassOrInterfaceType(NEW_USER));
            if (creation.getArguments().size() == 1) {
                creation.addArgument(StaticJavaParser.parseExpression("\"\""));
            }
            return;
        }
        if (OLD_SINGLE.endsWith("." + type) || OLD_SINGLE.equals(creation.getType().asString())) {
            rewriteSingleAuthCreation(creation);
        }
    }

    private static void rewriteSingleAuthCreation(final ObjectCreationExpr creation) {
        if (creation.getArguments().size() != 2) {
            return;
        }
        final Expression first = creation.getArgument(0);
        final Expression second = creation.getArgument(1);
        if (first.isMethodCallExpr() && second.isMethodCallExpr()) {
            final MethodCallExpr fname = first.asMethodCallExpr();
            final MethodCallExpr fpass = second.asMethodCallExpr();
            if (sameScope(fname, fpass) && "name".equals(fname.getNameAsString()) && "password".equals(fpass.getNameAsString())) {
                final Expression scope = fname.getScope().orElse(first);
                creation.setArgument(0, scope);
                creation.setArgument(1, StaticJavaParser.parseExpression(scope + ".authContext()"));
            }
        }
    }

    private static void rewriteMethodCall(final MethodCallExpr call) {
        if (!"user".equals(call.getNameAsString()) || call.getArguments().size() != 0) {
            return;
        }
        final Optional<Expression> scope = call.getScope();
        if (scope.isEmpty() || !scope.get().isNameExpr()) {
            return;
        }
        final String name = scope.get().toString();
        if (!name.endsWith("Authentication") && !name.endsWith("TestAuthentication")) {
            return;
        }
    }

    private static boolean sameScope(final MethodCallExpr first, final MethodCallExpr second) {
        return first.getScope().isPresent()
            && second.getScope().isPresent()
            && first.getScope().get().toString().equals(second.getScope().get().toString());
    }
}
