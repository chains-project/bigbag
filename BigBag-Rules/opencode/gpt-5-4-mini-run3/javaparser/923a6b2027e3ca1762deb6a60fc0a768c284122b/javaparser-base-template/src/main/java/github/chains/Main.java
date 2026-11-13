package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.stream.Stream;

public class Main {

    private static final String OLD_MATCHERS = "org.hamcrest.Matchers";

    private static final String NEW_MATCHERS = "org.hamcrest.CoreMatchers";

    public static void main(String[] args) {
        if (args.length != 1) {
            throw new IllegalArgumentException("Expected one source root path");
        }
        final Path root = Paths.get(args[0]);
        try (Stream<Path> paths = Files.walk(root)) {
            paths.filter(path -> path.toString().endsWith(".java"))
                .sorted(Comparator.naturalOrder())
                .forEach(Main::rewrite);
        } catch (final IOException err) {
            throw new IllegalStateException(err);
        }
    }

    private static void rewrite(final Path file) {
        try {
            final CompilationUnit cu = StaticJavaParser.parse(file);
            LexicalPreservingPrinter.setup(cu);
            boolean changed = false;
            for (final ImportDeclaration imp : cu.getImports()) {
                if (OLD_MATCHERS.equals(imp.getNameAsString())) {
                    imp.setName(NEW_MATCHERS);
                    changed = true;
                }
            }
            for (final MethodCallExpr call : cu.findAll(MethodCallExpr.class)) {
                changed |= rewriteScope(call);
                changed |= rewriteSpecialCalls(call);
            }
            for (final FieldAccessExpr access : cu.findAll(FieldAccessExpr.class)) {
                changed |= rewriteScope(access);
            }
            if (changed) {
                Files.writeString(file, LexicalPreservingPrinter.print(cu));
            }
        } catch (final IOException err) {
            throw new IllegalStateException(String.format("Failed to rewrite %s", file), err);
        }
    }

    private static boolean rewriteScope(final MethodCallExpr call) {
        if (call.getScope().isPresent()) {
            final Expression scope = call.getScope().get();
            if (isOldMatchers(scope)) {
                call.setScope(new NameExpr("CoreMatchers"));
                return true;
            }
        }
        return false;
    }

    private static boolean rewriteScope(final FieldAccessExpr access) {
        final Expression scope = access.getScope();
        if (isOldMatchers(scope)) {
            access.setScope(new NameExpr("CoreMatchers"));
            return true;
        }
        return false;
    }

    private static boolean rewriteSpecialCalls(final MethodCallExpr call) {
        if (!call.getScope().isPresent()) {
            return false;
        }
        final Expression scope = call.getScope().get();
        if (!isOldMatchers(scope) && !isCoreMatchers(scope)) {
            return false;
        }
        final String name = call.getNameAsString();
        if ("hasProperty".equals(name)) {
            call.setScope(new NameExpr("org.hamcrest.beans.HasPropertyWithValue"));
            return true;
        }
        if ("emptyIterableOf".equals(name) || "emptyIterable".equals(name)) {
            call.setScope(new NameExpr("org.hamcrest.collection.IsEmptyIterable"));
            return true;
        }
        return false;
    }

    private static boolean isOldMatchers(final Expression expr) {
        return expr.isNameExpr() && expr.asNameExpr().getNameAsString().equals("Matchers")
            || expr.toString().equals(OLD_MATCHERS);
    }

    private static boolean isCoreMatchers(final Expression expr) {
        return expr.isNameExpr() && expr.asNameExpr().getNameAsString().equals("CoreMatchers")
            || expr.toString().equals(NEW_MATCHERS);
    }
}
