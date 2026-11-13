package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.EnclosedExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

public class Main {
    private static final String LOCATION_LITERAL = "global";

    private static final Set<String> RESOURCE_ACCESSORS = new HashSet<>(Arrays.asList(
            "managedZones",
            "managedZoneOperations",
            "resourceRecordSets",
            "projects",
            "changes",
            "policies",
            "responsePolicies",
            "responsePolicyRules"
    ));

    private static final Map<String, Set<Integer>> OLD_ARITIES_BY_ACCESSOR_AND_METHOD = new HashMap<>();

    static {
        register("managedZones", "create", 2);
        register("managedZones", "delete", 2);
        register("managedZones", "get", 2);
        register("managedZones", "list", 1);
        register("managedZones", "patch", 3);
        register("managedZones", "update", 3);

        register("managedZoneOperations", "get", 3);
        register("managedZoneOperations", "list", 2);

        register("resourceRecordSets", "create", 3);
        register("resourceRecordSets", "delete", 4);
        register("resourceRecordSets", "get", 4);
        register("resourceRecordSets", "list", 2);
        register("resourceRecordSets", "patch", 5);
        register("resourceRecordSets", "update", 5);

        register("projects", "get", 1);

        register("changes", "create", 3);
        register("changes", "get", 3);
        register("changes", "list", 2);

        register("policies", "create", 2);
        register("policies", "delete", 2);
        register("policies", "get", 2);
        register("policies", "list", 1);
        register("policies", "patch", 3);
        register("policies", "update", 3);

        register("responsePolicies", "create", 2);
        register("responsePolicies", "delete", 2);
        register("responsePolicies", "get", 2);
        register("responsePolicies", "list", 1);
        register("responsePolicies", "patch", 3);
        register("responsePolicies", "update", 3);

        register("responsePolicyRules", "create", 3);
        register("responsePolicyRules", "delete", 3);
        register("responsePolicyRules", "get", 3);
        register("responsePolicyRules", "list", 2);
        register("responsePolicyRules", "patch", 4);
        register("responsePolicyRules", "update", 4);
    }

    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Usage: Main <source-directory>");
            System.exit(1);
        }

        Path root = Paths.get(args[0]).toAbsolutePath().normalize();
        if (!Files.isDirectory(root)) {
            System.err.println("Not a directory: " + root);
            System.exit(1);
        }

        try (Stream<Path> paths = Files.walk(root)) {
            paths.filter(p -> Files.isRegularFile(p) && p.toString().endsWith(".java"))
                    .forEach(Main::rewrite);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void register(String accessor, String method, int oldArity) {
        OLD_ARITIES_BY_ACCESSOR_AND_METHOD
                .computeIfAbsent(accessor + "#" + method, key -> new HashSet<>())
                .add(oldArity);
    }

    private static void rewrite(Path file) {
        try {
            String source = Files.readString(file, StandardCharsets.UTF_8);
            CompilationUnit cu = StaticJavaParser.parse(source);
            LexicalPreservingPrinter.setup(cu);

            boolean[] changed = new boolean[] {false};
            cu.findAll(MethodCallExpr.class).forEach(call -> {
                if (shouldAddLocation(call)) {
                    call.getArguments().add(1, new StringLiteralExpr(LOCATION_LITERAL));
                    changed[0] = true;
                }
            });

            if (changed[0]) {
                Files.writeString(file, LexicalPreservingPrinter.print(cu), StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to rewrite " + file, e);
        }
    }

    private static boolean shouldAddLocation(MethodCallExpr call) {
        Optional<String> accessor = findDnsAccessor(call.getScope());
        if (!accessor.isPresent()) {
            return false;
        }

        Set<Integer> oldArities = OLD_ARITIES_BY_ACCESSOR_AND_METHOD.get(accessor.get() + "#" + call.getNameAsString());
        return oldArities != null && oldArities.contains(call.getArguments().size());
    }

    private static Optional<String> findDnsAccessor(Optional<Expression> scope) {
        if (!scope.isPresent()) {
            return Optional.empty();
        }

        Expression expression = unwrap(scope.get());
        if (expression instanceof MethodCallExpr) {
            MethodCallExpr methodCall = (MethodCallExpr) expression;
            String name = methodCall.getNameAsString();
            if (RESOURCE_ACCESSORS.contains(name)) {
                return Optional.of(name);
            }
            return findDnsAccessor(methodCall.getScope());
        }

        if (expression instanceof FieldAccessExpr) {
            return findDnsAccessor(Optional.of(((FieldAccessExpr) expression).getScope()));
        }

        if (expression instanceof EnclosedExpr) {
            return findDnsAccessor(Optional.of(((EnclosedExpr) expression).getInner()));
        }

        if (expression instanceof NameExpr) {
            return Optional.empty();
        }

        return Optional.empty();
    }

    private static Expression unwrap(Expression expression) {
        Expression current = expression;
        while (current instanceof EnclosedExpr) {
            current = ((EnclosedExpr) current).getInner();
        }
        return current;
    }
}
