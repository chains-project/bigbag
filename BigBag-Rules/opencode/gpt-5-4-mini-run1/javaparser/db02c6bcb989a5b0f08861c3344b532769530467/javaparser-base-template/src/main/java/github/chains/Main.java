package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.BooleanLiteralExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

public class Main {

    private static final Map<String, Integer> DROP_FIRST_BOOLEAN_ARG = new HashMap<>();
    static {
        DROP_FIRST_BOOLEAN_ARG.put("StringContains", 1);
        DROP_FIRST_BOOLEAN_ARG.put("StringStartsWith", 1);
    }

    public static void main(String[] args) {
        if (args.length == 0) {
            throw new IllegalArgumentException("Expected source root path argument");
        }
        final Path root = Paths.get(args[0]);
        try (Stream<Path> files = Files.walk(root)) {
            files.filter(path -> path.toString().endsWith(".java")).forEach(Main::rewriteFile);
        } catch (final IOException err) {
            throw new IllegalStateException("Failed to traverse source tree: " + root, err);
        }
    }

    private static void rewriteFile(final Path path) {
        try {
            final CompilationUnit unit = StaticJavaParser.parse(path, StandardCharsets.UTF_8);
            LexicalPreservingPrinter.setup(unit);
            boolean changed = false;
            for (final ObjectCreationExpr expr : unit.findAll(ObjectCreationExpr.class)) {
                changed |= rewrite(expr);
            }
            for (final MethodCallExpr expr : unit.findAll(MethodCallExpr.class)) {
                changed |= rewrite(expr);
            }
            if (changed) {
                Files.write(path, LexicalPreservingPrinter.print(unit).getBytes(StandardCharsets.UTF_8));
            }
        } catch (final Exception err) {
            throw new IllegalStateException("Failed to rewrite " + path, err);
        }
    }

    private static Boolean rewrite(final ObjectCreationExpr expr) {
        final Integer drop = DROP_FIRST_BOOLEAN_ARG.get(expr.getType().getNameAsString());
        if (drop == null || expr.getArguments().size() <= drop) {
            return false;
        }
        final List<Expression> args = expr.getArguments();
        if (!(args.get(0) instanceof BooleanLiteralExpr)) {
            return false;
        }
        final NodeList<Expression> updated = new NodeList<>();
        for (int idx = drop; idx < args.size(); idx++) {
            updated.add(args.get(idx).clone());
        }
        expr.setArguments(updated);
        return true;
    }

    private static Boolean rewrite(final MethodCallExpr expr) {
        if (expr.getScope().isEmpty()) {
            return false;
        }
        final Expression scope = expr.getScope().get();
        if (!scope.isNameExpr()) {
            return false;
        }
        if (!"Matchers".equals(scope.asNameExpr().getNameAsString())) {
            return false;
        }
        final String type;
        if (expr.getNameAsString().startsWith("containsString")) {
            type = "StringContains";
        } else if (expr.getNameAsString().startsWith("startsWith")) {
            type = "StringStartsWith";
        } else {
            return false;
        }
        final NodeList<Expression> args = new NodeList<>();
        if (!expr.getArguments().isEmpty()) {
            args.add(expr.getArguments().get(expr.getArguments().size() - 1).clone());
        }
        final ObjectCreationExpr created = new ObjectCreationExpr(null, StaticJavaParser.parseClassOrInterfaceType(type), args);
        expr.replace(created);
        return true;
    }
}
