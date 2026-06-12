package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;

public class Main {
    private static final String OLD_TYPE = "org.apache.maven.shared.dependency.graph.internal.Maven31DependencyGraphBuilder";
    private static final String NEW_TYPE = "org.apache.maven.shared.dependency.graph.internal.DefaultDependencyGraphBuilder";

    public static void main(String[] args) {
        if (args.length != 1) {
            throw new IllegalArgumentException("Usage: Main <source-root>");
        }

        Path sourceRoot = Paths.get(args[0]);
        try {
            Files.walk(sourceRoot)
                .filter(path -> path.toString().endsWith(".java"))
                .sorted(Comparator.naturalOrder())
                .forEach(Main::transformFile);
        } catch (IOException e) {
            throw new RuntimeException("Failed to walk source tree: " + sourceRoot, e);
        }
    }

    private static void transformFile(Path path) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(path, StandardCharsets.UTF_8);

            boolean[] changed = {false};
            if (cu.getImports().removeIf(Main::isOldImport)) {
                changed[0] = true;
            }
            if (cu.findAll(MethodCallExpr.class).stream().anyMatch(Main::rewriteOptionalFallback)) {
                changed[0] = true;
            }
            if (cu.getImports().stream().noneMatch(imp -> imp.getNameAsString().equals(NEW_TYPE))
                && cu.findAll(ObjectCreationExpr.class).stream().anyMatch(expr -> matchesOldType(expr) || matchesNewType(expr))) {
                cu.addImport(NEW_TYPE);
                changed[0] = true;
            }
            cu.findAll(ObjectCreationExpr.class).forEach(expr -> {
                if (matchesOldType(expr) || matchesNewType(expr)) {
                    expr.setType(StaticJavaParser.parseClassOrInterfaceType(NEW_TYPE));
                    changed[0] = true;
                }
            });
            if (changed[0]) {
                Files.writeString(path, cu.toString(), StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to transform " + path, e);
        }
    }

    private static boolean matchesOldType(ObjectCreationExpr expr) {
        return expr.getType().getNameAsString().equals("Maven31DependencyGraphBuilder")
            || expr.getType().getNameAsString().equals(OLD_TYPE);
    }

    private static boolean matchesNewType(ObjectCreationExpr expr) {
        return expr.getType().getNameAsString().equals("DefaultDependencyGraphBuilder")
            || expr.getType().getNameAsString().equals(NEW_TYPE);
    }

    private static boolean rewriteOptionalFallback(MethodCallExpr methodCallExpr) {
        if (!"orElse".equals(methodCallExpr.getNameAsString()) || methodCallExpr.getArguments().size() != 1) {
            return false;
        }
        Expression argument = methodCallExpr.getArgument(0);
        if (!(argument instanceof ObjectCreationExpr) || !(methodCallExpr.getScope().isPresent())) {
            return false;
        }
        ObjectCreationExpr creation = (ObjectCreationExpr) argument;
        if (!matchesOldType(creation) && !matchesNewType(creation)) {
            return false;
        }
        Expression scope = methodCallExpr.getScope().get();
        if (!(scope instanceof MethodCallExpr)) {
            return false;
        }
        MethodCallExpr nullableCall = (MethodCallExpr) scope;
        if (!"ofNullable".equals(nullableCall.getNameAsString()) || nullableCall.getArguments().size() != 1) {
            return false;
        }
        methodCallExpr.replace(nullableCall.getArgument(0).clone());
        return true;
    }

    private static boolean isOldImport(ImportDeclaration importDeclaration) {
        return importDeclaration.getNameAsString().equals(OLD_TYPE);
    }
}
