package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.type.ClassOrInterfaceType;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

public class Main {
    private static final String OLD_CONNECTOR_IMPORT = "org.eclipse.jetty.server.nio.SelectChannelConnector";
    private static final String NEW_CONNECTOR_IMPORT = "org.eclipse.jetty.server.ServerConnector";
    private static final String HTTP_CONFIGURATION_IMPORT = "org.eclipse.jetty.server.HttpConfiguration";
    private static final String HTTP_CONNECTION_FACTORY_IMPORT = "org.eclipse.jetty.server.HttpConnectionFactory";

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            throw new IllegalArgumentException("Expected a single source root path");
        }

        Path root = Paths.get(args[0]);
        try (Stream<Path> paths = Files.walk(root)) {
            paths.filter(p -> Files.isRegularFile(p) && p.toString().endsWith(".java"))
                    .forEach(Main::transformFile);
        }
    }

    private static void transformFile(Path file) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(file);
            boolean changed = false;

            boolean hasOldConnector = cu.findAll(ObjectCreationExpr.class).stream().anyMatch(Main::isOldConnectorCreation);
            boolean hasServerSetter = cu.findAll(MethodCallExpr.class).stream().anyMatch(Main::isServerHeaderSetter);
            boolean needsHttpConfig = hasOldConnector || hasServerSetter;

            if (!hasOldConnector && !hasServerSetter) {
                return;
            }

            String serverName = findServerName(cu).orElse("server");
            String httpConfigName = findHttpConfigName(cu).orElse(null);

            if (needsHttpConfig && httpConfigName == null) {
                httpConfigName = uniqueName(cu, "httpConfiguration");
                insertHttpConfigDeclaration(cu, httpConfigName);
                changed = true;
            }

            for (MethodCallExpr call : cu.findAll(MethodCallExpr.class)) {
                if (isServerHeaderSetter(call)) {
                    call.setScope(StaticJavaParser.parseExpression(httpConfigName));
                    changed = true;
                }
            }

            for (ObjectCreationExpr creation : cu.findAll(ObjectCreationExpr.class)) {
                if (isOldConnectorCreation(creation)) {
                    creation.setType(new ClassOrInterfaceType(null, "ServerConnector"));
                    creation.setArguments(NodeList.nodeList(
                            StaticJavaParser.parseExpression(serverName),
                            new ObjectCreationExpr(null,
                                    new ClassOrInterfaceType(null, "HttpConnectionFactory"),
                                    NodeList.nodeList(StaticJavaParser.parseExpression(httpConfigName)))));
                    changed = true;
                }
            }

            for (VariableDeclarator var : cu.findAll(VariableDeclarator.class)) {
                if (isOldConnectorType(var) && isOldConnectorCreation(var.getInitializer().orElse(null))) {
                    var.setType(new ClassOrInterfaceType(null, "ServerConnector"));
                    changed = true;
                }
            }

            if (changed) {
                cu.getImports().removeIf(i -> i.getNameAsString().equals(OLD_CONNECTOR_IMPORT));
                addImport(cu, NEW_CONNECTOR_IMPORT);
                addImport(cu, HTTP_CONFIGURATION_IMPORT);
                addImport(cu, HTTP_CONNECTION_FACTORY_IMPORT);
                Files.writeString(file, cu.toString(), StandardCharsets.UTF_8);
            }
        } catch (Exception ignored) {
        }
    }

    private static Optional<String> findServerName(CompilationUnit cu) {
        return cu.findAll(VariableDeclarator.class).stream()
                .filter(v -> "Server".equals(simpleName(v.getTypeAsString())))
                .map(VariableDeclarator::getNameAsString)
                .findFirst();
    }

    private static Optional<String> findHttpConfigName(CompilationUnit cu) {
        return cu.findAll(VariableDeclarator.class).stream()
                .filter(v -> "HttpConfiguration".equals(simpleName(v.getTypeAsString())))
                .map(VariableDeclarator::getNameAsString)
                .findFirst();
    }

    private static boolean isOldConnectorCreation(Expression expression) {
        return expression instanceof ObjectCreationExpr
                && "SelectChannelConnector".equals(simpleName(((ObjectCreationExpr) expression).getTypeAsString()));
    }

    private static boolean isOldConnectorType(VariableDeclarator var) {
        String simple = simpleName(var.getTypeAsString());
        return "Connector".equals(simple) || "SelectChannelConnector".equals(simple);
    }

    private static boolean isServerHeaderSetter(MethodCallExpr call) {
        String name = call.getNameAsString();
        return "setSendServerVersion".equals(name) || "setSendDateHeader".equals(name);
    }

    private static void insertHttpConfigDeclaration(CompilationUnit cu, String name) {
        BlockStmt body = cu.findFirst(BlockStmt.class).orElse(null);
        if (body == null) {
            return;
        }
        VariableDeclarator declarator = new VariableDeclarator(new ClassOrInterfaceType(null, "HttpConfiguration"), name);
        declarator.setInitializer(new ObjectCreationExpr(null, new ClassOrInterfaceType(null, "HttpConfiguration"), new NodeList<>()));
        body.getStatements().add(0, new ExpressionStmt(new VariableDeclarationExpr(declarator)));
    }

    private static String uniqueName(CompilationUnit cu, String base) {
        String name = base;
        int i = 1;
        String text = cu.toString();
        while (text.contains(name)) {
            name = base + i++;
        }
        return name;
    }

    private static String simpleName(String typeName) {
        int dot = typeName.lastIndexOf('.');
        return dot >= 0 ? typeName.substring(dot + 1) : typeName;
    }

    private static void addImport(CompilationUnit cu, String fqcn) {
        if (cu.getImports().stream().noneMatch(i -> i.getNameAsString().equals(fqcn))) {
            cu.addImport(fqcn);
        }
    }
}
