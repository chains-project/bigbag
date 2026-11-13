package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.BodyDeclaration;
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
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public class Main {

    private static final String OLD_SERVLET_PREFIX = "javax.servlet";
    private static final String NEW_SERVLET_PREFIX = "jakarta.servlet";
    private static final String OLD_CONNECTOR = "org.eclipse.jetty.server.nio.SelectChannelConnector";
    private static final String NEW_CONNECTOR = "org.eclipse.jetty.server.ServerConnector";
    private static final String OLD_CONNECTOR_SIMPLE = "SelectChannelConnector";
    private static final String OLD_CONNECTOR_INTERFACE = "Connector";
    private static final String HTTP_CONFIGURATION = "org.eclipse.jetty.server.HttpConfiguration";
    private static final String HTTP_CONNECTION_FACTORY = "org.eclipse.jetty.server.HttpConnectionFactory";

    public static void main(String[] args) throws IOException {
        Path root = Paths.get(args.length > 0 ? args[0] : ".");
        Files.walk(root)
                .filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                .forEach(Main::transformFile);
    }

    private static void transformFile(Path file) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(file);
            LexicalPreservingPrinter.setup(cu);

            boolean changed = false;
            changed |= rewriteImports(cu);
            changed |= rewriteLegacyJettyBlocks(cu);

            if (changed) {
                Files.writeString(file, LexicalPreservingPrinter.print(cu));
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to transform " + file, e);
        }
    }

    private static boolean rewriteImports(CompilationUnit cu) {
        boolean changed = false;

        for (ImportDeclaration importDeclaration : new ArrayList<>(cu.getImports())) {
            String name = importDeclaration.getNameAsString();

            if (name.startsWith(OLD_SERVLET_PREFIX)) {
                importDeclaration.setName(name.replace(OLD_SERVLET_PREFIX, NEW_SERVLET_PREFIX));
                changed = true;
            }

            if (name.equals(OLD_CONNECTOR)) {
                importDeclaration.setName(NEW_CONNECTOR);
                changed = true;
            }
        }

        if (containsLegacyConnectorUsage(cu)) {
            ensureImport(cu, NEW_CONNECTOR);
        }

        if (containsLegacyServerConfigurationCall(cu)) {
            ensureImport(cu, HTTP_CONFIGURATION);
            ensureImport(cu, HTTP_CONNECTION_FACTORY);
        }

        return changed;
    }

    private static boolean rewriteLegacyJettyBlocks(CompilationUnit cu) {
        boolean changed = false;

        for (BodyDeclaration<?> bodyDeclaration : cu.findAll(BodyDeclaration.class)) {
            Optional<BlockStmt> maybeBody = extractBody(bodyDeclaration);
            if (maybeBody.isEmpty()) {
                continue;
            }

            BlockStmt body = maybeBody.get();
            boolean hasLegacyServerConfigCall = hasLegacyServerConfigurationCall(body);
            boolean hasLegacyConnector = hasLegacyConnectorCreation(body);

            if (!hasLegacyServerConfigCall && !hasLegacyConnector) {
                continue;
            }

            if (hasLegacyServerConfigCall) {
                changed |= rewriteServerConfigurationCalls(body);
                insertHttpConfigurationDeclaration(body);
                ensureImport(cu, HTTP_CONFIGURATION);
                ensureImport(cu, HTTP_CONNECTION_FACTORY);
            }

            if (hasLegacyConnector) {
                changed |= rewriteConnectorDeclarations(cu, body);
                changed |= rewriteConnectorCreation(body);
                ensureImport(cu, NEW_CONNECTOR);
            }
        }

        return changed;
    }

    private static Optional<BlockStmt> extractBody(BodyDeclaration<?> bodyDeclaration) {
        if (bodyDeclaration instanceof MethodDeclaration) {
            return ((MethodDeclaration) bodyDeclaration).getBody();
        }
        if (bodyDeclaration instanceof ConstructorDeclaration) {
            return ((ConstructorDeclaration) bodyDeclaration).getBody() == null
                    ? Optional.empty()
                    : Optional.of(((ConstructorDeclaration) bodyDeclaration).getBody());
        }
        return Optional.empty();
    }

    private static boolean hasLegacyServerConfigurationCall(BlockStmt body) {
        return body.findAll(MethodCallExpr.class).stream().anyMatch(Main::isLegacyServerConfigurationCall);
    }

    private static boolean containsLegacyServerConfigurationCall(CompilationUnit cu) {
        return cu.findAll(MethodCallExpr.class).stream().anyMatch(Main::isLegacyServerConfigurationCall);
    }

    private static boolean isLegacyServerConfigurationCall(MethodCallExpr call) {
        String name = call.getNameAsString();
        return "setSendServerVersion".equals(name) || "setSendDateHeader".equals(name);
    }

    private static boolean hasLegacyConnectorCreation(BlockStmt body) {
        return body.findAll(ObjectCreationExpr.class).stream()
                .anyMatch(expr -> OLD_CONNECTOR.endsWith(expr.getType().getNameAsString())
                        || OLD_CONNECTOR_SIMPLE.equals(expr.getType().getNameAsString()));
    }

    private static boolean containsLegacyConnectorUsage(CompilationUnit cu) {
        return cu.findAll(ObjectCreationExpr.class).stream()
                .anyMatch(expr -> OLD_CONNECTOR.endsWith(expr.getType().getNameAsString())
                        || OLD_CONNECTOR_SIMPLE.equals(expr.getType().getNameAsString()));
    }

    private static boolean rewriteServerConfigurationCalls(BlockStmt body) {
        boolean changed = false;
        for (MethodCallExpr call : body.findAll(MethodCallExpr.class)) {
            if (isLegacyServerConfigurationCall(call)) {
                call.setScope(new NameExpr("httpConfiguration"));
                changed = true;
            }
        }
        return changed;
    }

    private static void insertHttpConfigurationDeclaration(BlockStmt body) {
        if (body.findAll(VariableDeclarator.class).stream()
                .anyMatch(variableDeclarator -> "httpConfiguration".equals(variableDeclarator.getNameAsString()))) {
            return;
        }

        VariableDeclarator variable = new VariableDeclarator(new ClassOrInterfaceType(null, "HttpConfiguration"), "httpConfiguration");
        variable.setInitializer(new ObjectCreationExpr(null, new ClassOrInterfaceType(null, "HttpConfiguration"), new NodeList<>()));
        body.addStatement(0, new com.github.javaparser.ast.stmt.ExpressionStmt(new com.github.javaparser.ast.expr.VariableDeclarationExpr(variable)));
    }

    private static boolean rewriteConnectorCreation(BlockStmt body) {
        boolean changed = false;
        for (ObjectCreationExpr creation : body.findAll(ObjectCreationExpr.class)) {
            if (!(OLD_CONNECTOR.endsWith(creation.getType().getNameAsString())
                    || OLD_CONNECTOR_SIMPLE.equals(creation.getType().getNameAsString()))) {
                continue;
            }

            creation.setType(new ClassOrInterfaceType(null, "ServerConnector"));
            creation.setArguments(createServerConnectorArguments(body, creation));
            changed = true;
        }
        return changed;
    }

    private static boolean rewriteConnectorDeclarations(CompilationUnit cu, BlockStmt body) {
        boolean changed = false;
        Set<String> connectorNames = new java.util.HashSet<>();

        for (AssignExpr assignExpr : body.findAll(AssignExpr.class)) {
            if (!(assignExpr.getValue() instanceof ObjectCreationExpr)) {
                continue;
            }
            ObjectCreationExpr creation = (ObjectCreationExpr) assignExpr.getValue();
            if (!(OLD_CONNECTOR.endsWith(creation.getType().getNameAsString())
                    || OLD_CONNECTOR_SIMPLE.equals(creation.getType().getNameAsString()))) {
                continue;
            }

            extractAssignedName(assignExpr.getTarget()).ifPresent(connectorNames::add);
        }

        for (VariableDeclarator variableDeclarator : body.findAll(VariableDeclarator.class)) {
            if (!variableDeclarator.getInitializer().filter(init -> init instanceof ObjectCreationExpr).isPresent()) {
                continue;
            }
            ObjectCreationExpr creation = (ObjectCreationExpr) variableDeclarator.getInitializer().get();
            if (OLD_CONNECTOR.endsWith(creation.getType().getNameAsString())
                    || OLD_CONNECTOR_SIMPLE.equals(creation.getType().getNameAsString())) {
                connectorNames.add(variableDeclarator.getNameAsString());
                if (isConnectorLike(variableDeclarator.getType())) {
                    variableDeclarator.setType(new ClassOrInterfaceType(null, "ServerConnector"));
                    changed = true;
                }
            }
        }

        for (FieldDeclaration fieldDeclaration : cu.findAll(FieldDeclaration.class)) {
            for (VariableDeclarator variableDeclarator : fieldDeclaration.getVariables()) {
                if (!connectorNames.contains(variableDeclarator.getNameAsString())) {
                    continue;
                }
                if (isConnectorLike(variableDeclarator.getType())) {
                    variableDeclarator.setType(new ClassOrInterfaceType(null, "ServerConnector"));
                    changed = true;
                }
            }
        }

        for (VariableDeclarator variableDeclarator : body.findAll(VariableDeclarator.class)) {
            if (connectorNames.contains(variableDeclarator.getNameAsString()) && isConnectorLike(variableDeclarator.getType())) {
                variableDeclarator.setType(new ClassOrInterfaceType(null, "ServerConnector"));
                changed = true;
            }
        }

        return changed;
    }

    private static Optional<String> extractAssignedName(Expression target) {
        if (target instanceof NameExpr) {
            return Optional.of(((NameExpr) target).getNameAsString());
        }
        if (target instanceof FieldAccessExpr) {
            return Optional.of(((FieldAccessExpr) target).getNameAsString());
        }
        return Optional.empty();
    }

    private static boolean isConnectorLike(Type type) {
        if (!(type instanceof ClassOrInterfaceType)) {
            return false;
        }
        String name = ((ClassOrInterfaceType) type).getNameAsString();
        return OLD_CONNECTOR_INTERFACE.equals(name) || OLD_CONNECTOR_SIMPLE.equals(name);
    }

    private static NodeList<Expression> createServerConnectorArguments(BlockStmt body, ObjectCreationExpr creation) {
        NodeList<Expression> arguments = new NodeList<>();
        arguments.add(findServerExpression(body, creation));

        if (body.findAll(MethodCallExpr.class).stream().anyMatch(Main::isLegacyServerConfigurationCall)) {
            ObjectCreationExpr httpConnectionFactory = new ObjectCreationExpr(
                    null,
                    new ClassOrInterfaceType(null, "HttpConnectionFactory"),
                    new NodeList<>(new NameExpr("httpConfiguration")));
            arguments.add(httpConnectionFactory);
        }

        return arguments;
    }

    private static Expression findServerExpression(BlockStmt body, Node anchor) {
        Optional<Parameter> serverParameter = anchor.findAncestor(MethodDeclaration.class)
                .flatMap(method -> method.getParameters().stream().filter(parameter -> isServerType(parameter.getType())).findFirst());
        if (serverParameter.isPresent()) {
            return new NameExpr(serverParameter.get().getNameAsString());
        }

        Optional<VariableDeclarator> localServer = body.findAll(VariableDeclarator.class).stream()
                .filter(variable -> isServerType(variable.getType()))
                .findFirst();
        if (localServer.isPresent()) {
            return new NameExpr(localServer.get().getNameAsString());
        }

        Optional<VariableDeclarator> namedServer = body.findAll(VariableDeclarator.class).stream()
                .filter(variable -> "server".equals(variable.getNameAsString()))
                .findFirst();
        if (namedServer.isPresent()) {
            return new NameExpr(namedServer.get().getNameAsString());
        }

        return new NameExpr("server");
    }

    private static boolean isServerType(Type type) {
        if (!(type instanceof ClassOrInterfaceType)) {
            return false;
        }

        String name = ((ClassOrInterfaceType) type).getNameAsString();
        return "Server".equals(name) || name.endsWith(".Server");
    }

    private static void ensureImport(CompilationUnit cu, String fqcn) {
        boolean present = cu.getImports().stream().anyMatch(importDeclaration -> importDeclaration.getNameAsString().equals(fqcn));
        if (!present) {
            cu.addImport(fqcn);
        }
    }
}
