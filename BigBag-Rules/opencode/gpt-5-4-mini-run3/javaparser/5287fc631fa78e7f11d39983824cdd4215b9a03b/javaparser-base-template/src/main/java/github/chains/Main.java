package github.chains;

import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.Optional;

public class Main {
    public static void main(String[] args) throws IOException {
        Path root = args.length > 0 ? Paths.get(args[0]) : Paths.get(".");
        root = root.toAbsolutePath().normalize();

        StaticJavaParser.setConfiguration(new ParserConfiguration());

        Files.walk(root)
                .filter(p -> Files.isRegularFile(p) && p.toString().endsWith(".java"))
                .sorted(Comparator.naturalOrder())
                .forEach(Main::rewrite);
    }

    private static void rewrite(Path path) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(path);
            boolean changed = false;

            for (ObjectCreationExpr creation : cu.findAll(ObjectCreationExpr.class)) {
                if (!isOldConnector(creation)) {
                    continue;
                }

                Optional<Expression> serverExpr = inferServerExpression(creation);
                if (!serverExpr.isPresent()) {
                    continue;
                }

                creation.setType(new ClassOrInterfaceType(null, "ServerConnector"));
                creation.setArguments(NodeList.nodeList(serverExpr.get().clone()));
                changed = true;
            }

            if (changed) {
                cu.getImports().removeIf(i -> i.getNameAsString().endsWith("SelectChannelConnector"));
                if (!cu.getImports().stream().anyMatch(i -> i.getNameAsString().equals("org.eclipse.jetty.server.ServerConnector"))) {
                    cu.addImport("org.eclipse.jetty.server.ServerConnector");
                }
                Files.write(path, cu.toString().getBytes(StandardCharsets.UTF_8));
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to rewrite " + path, e);
        }
    }

    private static boolean isOldConnector(ObjectCreationExpr creation) {
        String type = creation.getTypeAsString();
        return "SelectChannelConnector".equals(type) || type.endsWith(".SelectChannelConnector");
    }

    private static Optional<Expression> inferServerExpression(ObjectCreationExpr creation) {
        Optional<String> connectorName = inferConnectorName(creation);
        if (connectorName.isPresent()) {
            Optional<Expression> fromAddConnector = findServerFromAddConnector(creation, connectorName.get());
            if (fromAddConnector.isPresent()) {
                return fromAddConnector;
            }
        }

        return findServerInEnclosingScope(creation);
    }

    private static Optional<String> inferConnectorName(ObjectCreationExpr creation) {
        Optional<VariableDeclarator> var = creation.findAncestor(VariableDeclarator.class);
        if (var.isPresent()) {
            return Optional.of(var.get().getNameAsString());
        }

        Optional<com.github.javaparser.ast.expr.AssignExpr> assign = creation.findAncestor(com.github.javaparser.ast.expr.AssignExpr.class);
        if (assign.isPresent()) {
            Expression target = assign.get().getTarget();
            if (target.isNameExpr()) {
                return Optional.of(target.asNameExpr().getNameAsString());
            }
            if (target.isFieldAccessExpr()) {
                return Optional.of(target.asFieldAccessExpr().getNameAsString());
            }
        }

        return Optional.empty();
    }

    private static Optional<Expression> findServerFromAddConnector(ObjectCreationExpr creation, String connectorName) {
        return creation.findAncestor(Node.class).flatMap(scope -> {
            for (MethodCallExpr call : scope.findAll(MethodCallExpr.class)) {
                if (!"addConnector".equals(call.getNameAsString()) || call.getArguments().size() != 1) {
                    continue;
                }
                if (!argumentMatches(call.getArgument(0), connectorName)) {
                    continue;
                }
                if (call.getScope().isPresent()) {
                    return Optional.of(call.getScope().get());
                }
            }
            return Optional.empty();
        });
    }

    private static boolean argumentMatches(Expression arg, String connectorName) {
        if (arg.isNameExpr()) {
            return connectorName.equals(arg.asNameExpr().getNameAsString());
        }
        if (arg.isFieldAccessExpr()) {
            return connectorName.equals(arg.asFieldAccessExpr().getNameAsString());
        }
        return arg.toString().equals(connectorName) || arg.toString().endsWith("." + connectorName);
    }

    private static Optional<Expression> findServerInEnclosingScope(ObjectCreationExpr creation) {
        return creation.findAncestor(MethodDeclaration.class)
                .flatMap(Main::findServerVariableInMethod)
                .or(() -> creation.findAncestor(ConstructorDeclaration.class)
                        .flatMap(Main::findServerVariableInConstructor))
                .or(() -> creation.findAncestor(ClassOrInterfaceDeclaration.class).flatMap(Main::findServerFieldInScope));
    }

    private static Optional<Expression> findServerVariableInMethod(MethodDeclaration method) {
        for (VariableDeclarator variable : method.findAll(VariableDeclarator.class)) {
            if (looksLikeServerType(variable.getTypeAsString()) && looksLikeServerName(variable.getNameAsString())) {
                return Optional.of(new NameExpr(variable.getNameAsString()));
            }
        }
        return Optional.empty();
    }

    private static Optional<Expression> findServerVariableInConstructor(ConstructorDeclaration constructor) {
        for (VariableDeclarator variable : constructor.findAll(VariableDeclarator.class)) {
            if (looksLikeServerType(variable.getTypeAsString()) && looksLikeServerName(variable.getNameAsString())) {
                return Optional.of(new NameExpr(variable.getNameAsString()));
            }
        }
        return Optional.empty();
    }

    private static Optional<Expression> findServerFieldInScope(ClassOrInterfaceDeclaration scope) {
        for (FieldDeclaration field : scope.getFields()) {
            for (VariableDeclarator variable : field.getVariables()) {
                if (looksLikeServerType(variable.getTypeAsString()) && looksLikeServerName(variable.getNameAsString())) {
                    return Optional.of(new NameExpr(variable.getNameAsString()));
                }
            }
        }
        return Optional.empty();
    }

    private static boolean looksLikeServerType(String type) {
        return "Server".equals(type) || type.endsWith(".Server") || type.endsWith("org.eclipse.jetty.server.Server");
    }

    private static boolean looksLikeServerName(String name) {
        return "server".equals(name) || name.endsWith("Server") || name.endsWith("server");
    }
}
