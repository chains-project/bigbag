package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;

public class Main {
    private static final String LOCATION = "global";

    private static final Set<Rule> RULES = new HashSet<>(Arrays.asList(
            new Rule("managedZones", "create", 2),
            new Rule("managedZones", "get", 2),
            new Rule("managedZones", "list", 1),
            new Rule("managedZones", "delete", 2),
            new Rule("resourceRecordSets", "list", 2),
            new Rule("projects", "get", 1),
            new Rule("changes", "create", 3),
            new Rule("changes", "get", 3),
            new Rule("changes", "list", 2)
    ));

    public static void main(String[] args) {
        if (args.length < 1) {
            throw new IllegalArgumentException("Expected source directory path as the first argument");
        }

        Path sourceRoot = Paths.get(args[0]);
        if (!Files.isDirectory(sourceRoot)) {
            throw new IllegalArgumentException("Source directory does not exist: " + sourceRoot);
        }

        JavaParser parser = new JavaParser(new ParserConfiguration());
        try (Stream<Path> paths = Files.walk(sourceRoot)) {
            paths.filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                    .forEach(path -> process(parser, path));
        } catch (IOException ex) {
            throw new RuntimeException("Failed to walk source tree", ex);
        }
    }

    private static void process(JavaParser parser, Path path) {
        try {
            CompilationUnit cu = parser.parse(path).getResult()
                    .orElseThrow(() -> new IllegalStateException("Failed to parse " + path));
            LexicalPreservingPrinter.setup(cu);

            boolean changed = false;
            for (MethodCallExpr call : cu.findAll(MethodCallExpr.class)) {
                changed |= rewrite(call);
            }

            if (changed) {
                Files.writeString(path, LexicalPreservingPrinter.print(cu));
            }
        } catch (IOException ex) {
            throw new RuntimeException("Failed to process " + path, ex);
        }
    }

    private static boolean rewrite(MethodCallExpr call) {
        for (Rule rule : RULES) {
            if (!call.getNameAsString().equals(rule.methodName)) {
                continue;
            }
            if (call.getArguments().size() != rule.oldArity) {
                continue;
            }
            if (!matchesScope(call, rule.scopeMethodName)) {
                continue;
            }

            NodeList<Expression> updated = new NodeList<>();
            int insertAt = Math.min(1, call.getArguments().size());
            boolean inserted = false;
            for (int i = 0; i < call.getArguments().size(); i++) {
                if (i == insertAt) {
                    updated.add(new StringLiteralExpr(LOCATION));
                    inserted = true;
                }
                updated.add(call.getArguments().get(i));
            }
            if (!inserted) {
                updated.add(new StringLiteralExpr(LOCATION));
            }
            call.setArguments(updated);
            return true;
        }
        return false;
    }

    private static boolean matchesScope(MethodCallExpr call, String expectedScopeMethod) {
        return call.getScope()
                .filter(MethodCallExpr.class::isInstance)
                .map(MethodCallExpr.class::cast)
                .map(scope -> scope.getNameAsString().equals(expectedScopeMethod))
                .orElse(false);
    }

    private static final class Rule {
        private final String scopeMethodName;
        private final String methodName;
        private final int oldArity;

        private Rule(String scopeMethodName, String methodName, int oldArity) {
            this.scopeMethodName = scopeMethodName;
            this.methodName = methodName;
            this.oldArity = oldArity;
        }
    }
}
