package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.type.Type;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class Main {

    public static void main(String[] args) throws IOException {
        if (args.length == 0) {
            throw new IllegalArgumentException("Usage: Main <source-root>");
        }

        Path root = Paths.get(args[0]);
        try (var paths = Files.walk(root)) {
            paths.filter(p -> p.toString().endsWith(".java"))
                    .forEach(Main::transformFileSafely);
        }
    }

    private static void transformFileSafely(Path path) {
        try {
            transformFile(path);
        } catch (IOException e) {
            throw new RuntimeException("Failed to transform " + path, e);
        }
    }

    private static void transformFile(Path path) throws IOException {
        CompilationUnit cu = StaticJavaParser.parse(path);
        boolean changed = false;

        changed |= rewriteServletImports(cu);

        Map<BlockStmt, String> httpConfigurations = new IdentityHashMap<>();
        changed |= rewriteServerConfigurationCalls(cu, httpConfigurations);
        changed |= rewriteSelectChannelConnectorUsages(cu, httpConfigurations);

        if (changed) {
            Files.writeString(path, cu.toString(), StandardCharsets.UTF_8);
        }
    }

    private static boolean rewriteServletImports(CompilationUnit cu) {
        boolean changed = false;
        for (ImportDeclaration importDeclaration : cu.getImports()) {
            String name = importDeclaration.getNameAsString();
            if (name.startsWith("javax.servlet")) {
                importDeclaration.setName(name.replaceFirst("^javax\\.servlet", "jakarta.servlet"));
                changed = true;
            }
        }
        changed |= cu.getImports().removeIf(importDeclaration ->
                importDeclaration.getNameAsString().equals("org.eclipse.jetty.server.nio.SelectChannelConnector"));
        return changed;
    }

    private static boolean rewriteServerConfigurationCalls(CompilationUnit cu, Map<BlockStmt, String> httpConfigurations) {
        boolean changed = false;
        for (MethodCallExpr call : cu.findAll(MethodCallExpr.class)) {
            if (!isLegacyServerConfigurationCall(call)) {
                continue;
            }

            Optional<BlockStmt> blockOpt = call.findAncestor(BlockStmt.class);
            if (blockOpt.isEmpty()) {
                continue;
            }

            BlockStmt block = blockOpt.get();
            String variableName = httpConfigurations.computeIfAbsent(block, Main::createHttpConfigurationVariable);
            ensureHttpConfigurationDeclaration(block, variableName);
            call.setScope(new NameExpr(variableName));
            changed = true;
        }
        return changed;
    }

    private static boolean isLegacyServerConfigurationCall(MethodCallExpr call) {
        String name = call.getNameAsString();
        return "setSendServerVersion".equals(name) || "setSendDateHeader".equals(name);
    }

    private static String createHttpConfigurationVariable(BlockStmt block) {
        Set<String> names = new HashSet<>();
        for (VariableDeclarator declarator : block.findAll(VariableDeclarator.class)) {
            names.add(declarator.getNameAsString());
        }

        String base = "httpConfiguration";
        if (!names.contains(base)) {
            return base;
        }

        int index = 1;
        while (names.contains(base + index)) {
            index++;
        }
        return base + index;
    }

    private static void ensureHttpConfigurationDeclaration(BlockStmt block, String variableName) {
        boolean alreadyDeclared = block.findAll(VariableDeclarator.class).stream()
                .anyMatch(declarator -> variableName.equals(declarator.getNameAsString())
                        && declarator.getType().asString().contains("HttpConfiguration"));
        if (alreadyDeclared) {
            return;
        }

        Statement statement = StaticJavaParser.parseStatement(
                "org.eclipse.jetty.server.HttpConfiguration " + variableName + " = new org.eclipse.jetty.server.HttpConfiguration();");
        block.addStatement(0, statement);
    }

    private static boolean rewriteSelectChannelConnectorUsages(CompilationUnit cu, Map<BlockStmt, String> httpConfigurations) {
        boolean changed = false;
        Set<String> connectorVariableNames = collectLegacyConnectorVariableNames(cu);

        for (VariableDeclarator declarator : cu.findAll(VariableDeclarator.class)) {
            if (connectorVariableNames.contains(declarator.getNameAsString()) && isLegacyConnectorType(declarator.getType())) {
                declarator.setType(StaticJavaParser.parseClassOrInterfaceType("org.eclipse.jetty.server.ServerConnector"));
                changed = true;
            }
        }

        for (ObjectCreationExpr creation : cu.findAll(ObjectCreationExpr.class)) {
            if (!isSelectChannelConnectorCreation(creation)) {
                continue;
            }

            Optional<BlockStmt> blockOpt = creation.findAncestor(BlockStmt.class);
            if (blockOpt.isEmpty()) {
                continue;
            }

            String serverName = findServerName(creation).orElse(null);
            if (serverName == null) {
                continue;
            }

            BlockStmt block = blockOpt.get();
            String httpConfigurationName = httpConfigurations.get(block);
            if (httpConfigurationName == null) {
                creation.setType(StaticJavaParser.parseClassOrInterfaceType("org.eclipse.jetty.server.ServerConnector"));
                creation.setArguments(NodeList.nodeList(new NameExpr(serverName)));
            } else {
                creation.setType(StaticJavaParser.parseClassOrInterfaceType("org.eclipse.jetty.server.ServerConnector"));
                creation.setArguments(NodeList.nodeList(
                        new NameExpr(serverName),
                        new ObjectCreationExpr(null,
                                StaticJavaParser.parseClassOrInterfaceType("org.eclipse.jetty.server.HttpConnectionFactory"),
                                NodeList.nodeList(new NameExpr(httpConfigurationName)))));
            }
            changed = true;
        }

        return changed;
    }

    private static boolean isLegacyConnectorType(Type type) {
        String name = type.asString();
        return "Connector".equals(name)
                || name.endsWith(".Connector")
                || "SelectChannelConnector".equals(name)
                || name.endsWith(".SelectChannelConnector");
    }

    private static boolean isSelectChannelConnectorCreation(ObjectCreationExpr creation) {
        String name = creation.getType().getNameAsString();
        return "SelectChannelConnector".equals(name) || name.endsWith(".SelectChannelConnector");
    }

    private static Set<String> collectLegacyConnectorVariableNames(CompilationUnit cu) {
        Set<String> names = new HashSet<>();

        for (VariableDeclarator declarator : cu.findAll(VariableDeclarator.class)) {
            if (declarator.getInitializer().filter(expr -> expr.isObjectCreationExpr() && isSelectChannelConnectorCreation(expr.asObjectCreationExpr())).isPresent()) {
                names.add(declarator.getNameAsString());
            }
        }

        for (AssignExpr assignment : cu.findAll(AssignExpr.class)) {
            if (!assignment.getValue().isObjectCreationExpr()) {
                continue;
            }
            if (!isSelectChannelConnectorCreation(assignment.getValue().asObjectCreationExpr())) {
                continue;
            }

            extractAssignedName(assignment.getTarget()).ifPresent(names::add);
        }

        for (MethodCallExpr call : cu.findAll(MethodCallExpr.class)) {
            if (!isPortAccessCall(call)) {
                continue;
            }
            call.getScope().flatMap(Main::extractExpressionName).ifPresent(names::add);
        }

        return names;
    }

    private static boolean isPortAccessCall(MethodCallExpr call) {
        String name = call.getNameAsString();
        return "setPort".equals(name) || "getLocalPort".equals(name);
    }

    private static Optional<String> extractAssignedName(Expression target) {
        if (target.isNameExpr()) {
            return Optional.of(target.asNameExpr().getNameAsString());
        }
        if (target.isFieldAccessExpr()) {
            return Optional.of(target.asFieldAccessExpr().getNameAsString());
        }
        return Optional.empty();
    }

    private static Optional<String> extractExpressionName(Expression expression) {
        if (expression.isNameExpr()) {
            return Optional.of(expression.asNameExpr().getNameAsString());
        }
        if (expression.isFieldAccessExpr()) {
            return Optional.of(expression.asFieldAccessExpr().getNameAsString());
        }
        return Optional.empty();
    }

    private static Optional<String> findServerName(Node node) {
        Optional<String> fromParameters = node.findAncestor(MethodDeclaration.class)
                .flatMap(Main::findServerParameterName);
        if (fromParameters.isPresent()) {
            return fromParameters;
        }

        Optional<String> fromConstructors = node.findAncestor(ConstructorDeclaration.class)
                .flatMap(Main::findServerParameterName);
        if (fromConstructors.isPresent()) {
            return fromConstructors;
        }

        return node.findAncestor(com.github.javaparser.ast.body.ClassOrInterfaceDeclaration.class)
                .flatMap(Main::findServerFieldName);
    }

    private static Optional<String> findServerParameterName(MethodDeclaration declaration) {
        return declaration.getParameters().stream()
                .filter(Main::isServerParameter)
                .map(Parameter::getNameAsString)
                .findFirst();
    }

    private static Optional<String> findServerParameterName(ConstructorDeclaration declaration) {
        return declaration.getParameters().stream()
                .filter(Main::isServerParameter)
                .map(Parameter::getNameAsString)
                .findFirst();
    }

    private static boolean isServerParameter(Parameter parameter) {
        String type = parameter.getType().asString();
        return "Server".equals(type) || type.endsWith(".Server");
    }

    private static Optional<String> findServerFieldName(com.github.javaparser.ast.body.ClassOrInterfaceDeclaration declaration) {
        return declaration.getFields().stream()
                .flatMap(field -> field.getVariables().stream())
                .filter(variable -> isServerType(variable.getType()))
                .map(VariableDeclarator::getNameAsString)
                .findFirst();
    }

    private static boolean isServerType(Type type) {
        String name = type.asString();
        return "Server".equals(name) || name.endsWith(".Server");
    }
}
