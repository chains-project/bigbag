package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.expr.SimpleName;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.Optional;
import java.util.stream.Stream;

public class Main {
    private static final String OLD_CONNECTOR = "org.eclipse.jetty.server.nio.SelectChannelConnector";
    private static final String NEW_CONNECTOR = "org.eclipse.jetty.server.ServerConnector";

    public static void main(String[] args) throws Exception {
        Path sourceRoot = Paths.get(args.length > 0 ? args[0] : ".");
        try (Stream<Path> paths = Files.walk(sourceRoot)) {
            paths.filter(path -> path.toString().endsWith(".java"))
                .sorted(Comparator.naturalOrder())
                .forEach(Main::rewriteFileSafely);
        }
    }

    private static void rewriteFileSafely(Path path) {
        try {
            rewriteFile(path);
        } catch (Exception e) {
            throw new RuntimeException("Failed to rewrite " + path, e);
        }
    }

    private static void rewriteFile(Path path) throws IOException {
        CompilationUnit cu = StaticJavaParser.parse(path);
        boolean changed = false;

        for (ObjectCreationExpr creation : cu.findAll(ObjectCreationExpr.class)) {
            if (!isOldConnectorCreation(creation)) {
                continue;
            }

            Optional<Expression> serverExpression = findServerExpression(creation);
            if (!serverExpression.isPresent()) {
                continue;
            }

            creation.setType(StaticJavaParser.parseClassOrInterfaceType("ServerConnector"));
            creation.setArguments(new NodeList<>(serverExpression.get().clone()));
            changed = true;
        }

        for (ClassOrInterfaceType type : cu.findAll(ClassOrInterfaceType.class)) {
            if (isOldConnectorType(type)) {
                type.setName(new SimpleName("ServerConnector"));
                changed = true;
            }
        }

        if (changed) {
            cu.addImport(NEW_CONNECTOR);
            cu.getImports().removeIf(importDecl -> OLD_CONNECTOR.equals(importDecl.getNameAsString()));
            Files.write(path, cu.toString().getBytes(StandardCharsets.UTF_8));
        }
    }

    private static boolean isOldConnectorCreation(ObjectCreationExpr creation) {
        return creation.getType().getNameAsString().equals("SelectChannelConnector")
            || OLD_CONNECTOR.equals(creation.getType().getNameAsString());
    }

    private static boolean isOldConnectorType(ClassOrInterfaceType type) {
        return type.getNameAsString().equals("SelectChannelConnector");
    }

    private static Optional<Expression> findServerExpression(ObjectCreationExpr creation) {
        String connectorName = findAssignedName(creation);
        if (connectorName == null) {
            return Optional.empty();
        }

        Optional<CompilationUnit> compilationUnit = creation.findCompilationUnit();
        if (!compilationUnit.isPresent()) {
            return Optional.empty();
        }

        for (MethodCallExpr call : compilationUnit.get().findAll(MethodCallExpr.class)) {
            if (!"addConnector".equals(call.getNameAsString()) || !call.getScope().isPresent()) {
                continue;
            }

            if (call.getArguments().stream().anyMatch(argument -> sameSimpleName(argument, connectorName))) {
                return call.getScope();
            }
        }

        return Optional.empty();
    }

    private static String findAssignedName(ObjectCreationExpr creation) {
        Optional<VariableDeclarator> variable = creation.findAncestor(VariableDeclarator.class);
        if (variable.isPresent()) {
            return variable.get().getNameAsString();
        }

        Optional<AssignExpr> assignExpr = creation.findAncestor(AssignExpr.class);
        if (assignExpr.isPresent()) {
            Expression target = assignExpr.get().getTarget();
            if (target.isNameExpr()) {
                return target.asNameExpr().getNameAsString();
            }
            if (target.isFieldAccessExpr()) {
                return target.asFieldAccessExpr().getNameAsString();
            }
        }

        return null;
    }

    private static boolean sameSimpleName(Expression expression, String name) {
        if (expression.isNameExpr()) {
            return name.equals(expression.asNameExpr().getNameAsString());
        }
        if (expression.isFieldAccessExpr()) {
            return name.equals(expression.asFieldAccessExpr().getNameAsString());
        }
        return false;
    }
}
