package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.InitializerDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.type.ClassOrInterfaceType;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class Main {
    private static final String OLD_SERVLET_PREFIX = "javax.servlet";
    private static final String NEW_SERVLET_PREFIX = "jakarta.servlet";
    private static final String OLD_CONNECTOR_SIMPLE = "SelectChannelConnector";
    private static final String NEW_CONNECTOR_SIMPLE = "ServerConnector";
    private static final String OLD_CONNECTOR_FQCN = "org.eclipse.jetty.server.nio.SelectChannelConnector";
    private static final String NEW_CONNECTOR_FQCN = "org.eclipse.jetty.server.ServerConnector";

    public static void main(String[] args) {
        try {
            Path root = args.length > 0 ? Paths.get(args[0]) : Paths.get(".");
            Files.walk(root)
                    .filter(path -> path.toString().endsWith(".java"))
                    .forEach(Main::transformFile);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void transformFile(Path path) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(path);

            boolean changed = rewriteImports(cu);
            changed |= rewriteConnectorTypes(cu);
            changed |= rewriteConnectorCreations(cu);

            if (changed) {
                Files.writeString(path, cu.toString(), StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to transform " + path, e);
        }
    }

    private static boolean rewriteImports(CompilationUnit cu) {
        boolean changed = false;

        for (int i = 0; i < cu.getImports().size(); i++) {
            String name = cu.getImport(i).getNameAsString();
            if (name.startsWith(OLD_SERVLET_PREFIX)) {
                cu.getImport(i).setName(name.replace(OLD_SERVLET_PREFIX, NEW_SERVLET_PREFIX));
                changed = true;
            } else if (name.equals(OLD_CONNECTOR_FQCN)) {
                cu.getImport(i).setName(NEW_CONNECTOR_FQCN);
                changed = true;
            }
        }

        if (containsOldConnectorCreation(cu) && cu.getImports().stream().noneMatch(i -> i.getNameAsString().equals(NEW_CONNECTOR_FQCN))) {
            cu.addImport(NEW_CONNECTOR_FQCN);
            changed = true;
        }

        return changed;
    }

    private static boolean containsOldConnectorCreation(CompilationUnit cu) {
        return cu.findAll(ObjectCreationExpr.class).stream()
                .anyMatch(expr -> expr.getType().getNameAsString().equals(OLD_CONNECTOR_SIMPLE));
    }

    private static boolean rewriteConnectorTypes(CompilationUnit cu) {
        boolean changed = false;

        for (ClassOrInterfaceType type : cu.findAll(ClassOrInterfaceType.class)) {
            if (type.getNameAsString().equals(OLD_CONNECTOR_SIMPLE)) {
                type.setName(NEW_CONNECTOR_SIMPLE);
                changed = true;
            }
        }

        return changed;
    }

    private static boolean rewriteConnectorCreations(CompilationUnit cu) {
        boolean changed = false;
        for (MethodDeclaration method : cu.findAll(MethodDeclaration.class)) {
            changed |= rewriteBlock(method.getBody().orElse(null));
        }
        for (ConstructorDeclaration constructor : cu.findAll(ConstructorDeclaration.class)) {
            changed |= rewriteBlock(constructor.getBody());
        }
        for (InitializerDeclaration initializer : cu.findAll(InitializerDeclaration.class)) {
            changed |= rewriteBlock(initializer.getBody());
        }
        return changed;
    }

    private static boolean rewriteBlock(BlockStmt body) {
        if (body == null) {
            return false;
        }

        String serverName = firstServerName(body);
        Map<String, String> connectorServers = new HashMap<>();
        for (MethodCallExpr call : body.findAll(MethodCallExpr.class)) {
            if (!"addConnector".equals(call.getNameAsString()) || call.getArguments().size() != 1 || call.getScope().isEmpty()) {
                continue;
            }

            String connectorName = extractName(call.getArgument(0));
            if (connectorName != null) {
                connectorServers.put(connectorName, call.getScope().get().toString());
            }
        }

        String fallbackServerExpr = null;
        if (!connectorServers.isEmpty()) {
            fallbackServerExpr = connectorServers.values().iterator().next();
        } else if (serverName != null) {
            fallbackServerExpr = serverName;
        }

        boolean changed = false;
        for (ObjectCreationExpr creation : new ArrayList<>(body.findAll(ObjectCreationExpr.class))) {
            if (!creation.getType().getNameAsString().equals(OLD_CONNECTOR_SIMPLE)) {
                continue;
            }

            String connectorName = connectorVariableName(creation);
            String serverExpr = connectorName == null ? null : connectorServers.get(connectorName);
            if (serverExpr == null) {
                serverExpr = fallbackServerExpr;
            }

            if (serverExpr != null) {
                creation.replace(StaticJavaParser.parseExpression("new " + NEW_CONNECTOR_SIMPLE + "(" + serverExpr + ")"));
                changed = true;
            }
        }

        return changed;
    }

    private static String connectorVariableName(Node node) {
        Node parent = node.getParentNode().orElse(null);
        if (parent instanceof AssignExpr) {
            return extractName(((AssignExpr) parent).getTarget());
        }
        if (parent instanceof VariableDeclarator) {
            return ((VariableDeclarator) parent).getNameAsString();
        }
        return null;
    }

    private static String firstServerName(BlockStmt body) {
        ClassOrInterfaceDeclaration owner = body.findAncestor(ClassOrInterfaceDeclaration.class).orElse(null);
        if (owner != null) {
            for (FieldDeclaration field : owner.getFields()) {
                if (field.getVariables().size() == 1 && isServerType(field.getVariables().get(0).getType().asString())) {
                    return field.getVariables().get(0).getNameAsString();
                }
            }
        }

        for (FieldDeclaration field : body.findAll(FieldDeclaration.class)) {
            if (field.getVariables().size() == 1 && isServerType(field.getVariables().get(0).getType().asString())) {
                return field.getVariables().get(0).getNameAsString();
            }
        }
        return body.findAll(VariableDeclarator.class).stream()
                .filter(v -> isServerType(v.getType().asString()))
                .map(VariableDeclarator::getNameAsString)
                .findFirst()
                .orElse(null);
    }

    private static boolean isServerType(String typeName) {
        return "Server".equals(typeName) || "org.eclipse.jetty.server.Server".equals(typeName);
    }

    private static String extractName(Expression expression) {
        if (expression instanceof NameExpr) {
            return ((NameExpr) expression).getNameAsString();
        }
        if (expression instanceof FieldAccessExpr) {
            return ((FieldAccessExpr) expression).getNameAsString();
        }
        return null;
    }
}
