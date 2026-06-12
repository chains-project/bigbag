package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.Optional;

public class Main {
    private static final String OLD_CONNECTOR = "org.eclipse.jetty.server.nio.SelectChannelConnector";
    private static final String OLD_SERVER = "org.eclipse.jetty.server.Server";
    private static final String OLD_CONNECTOR_INTERFACE = "org.eclipse.jetty.server.Connector";
    private static final String OLD_HTTP_SERVLET_PREFIX = "javax.servlet";

    private static final String NEW_CONNECTOR = "org.eclipse.jetty.server.ServerConnector";
    private static final String NEW_HTTP_CONFIGURATION = "org.eclipse.jetty.server.HttpConfiguration";
    private static final String NEW_HTTP_CONNECTION_FACTORY = "org.eclipse.jetty.server.HttpConnectionFactory";
    private static final String NEW_HTTP_SERVLET_PREFIX = "jakarta.servlet";

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException("Expected a source root path");
        }

        Path sourceRoot = Paths.get(args[0]);
        if (!Files.isDirectory(sourceRoot)) {
            throw new IllegalArgumentException("Not a directory: " + sourceRoot);
        }

        Files.walk(sourceRoot)
                .filter(p -> Files.isRegularFile(p) && p.toString().endsWith(".java"))
                .sorted(Comparator.naturalOrder())
                .forEach(Main::transformFile);
    }

    private static void transformFile(Path path) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(path, StandardCharsets.UTF_8);
            LexicalPreservingPrinter.setup(cu);

            boolean changed = false;
            changed |= rewriteImports(cu);
            changed |= rewriteServerConfigurationCalls(cu);
            changed |= rewriteConnectorCreations(cu);

            if (changed) {
                Files.writeString(path, LexicalPreservingPrinter.print(cu), StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to transform " + path, e);
        }
    }

    private static boolean rewriteImports(CompilationUnit cu) {
        boolean changed = false;

        if (cu.getImports().removeIf(i -> i.getNameAsString().equals(OLD_CONNECTOR))) {
            cu.addImport(NEW_CONNECTOR);
            changed = true;
        }

        if (cu.getImports().stream().noneMatch(i -> i.getNameAsString().equals(NEW_HTTP_CONFIGURATION))) {
            if (usesHttpConfiguration(cu)) {
                cu.addImport(NEW_HTTP_CONFIGURATION);
                changed = true;
            }
        }

        if (cu.getImports().stream().noneMatch(i -> i.getNameAsString().equals(NEW_HTTP_CONNECTION_FACTORY))) {
            if (usesHttpConnectionFactory(cu)) {
                cu.addImport(NEW_HTTP_CONNECTION_FACTORY);
                changed = true;
            }
        }

        for (int i = 0; i < cu.getImports().size(); i++) {
            String name = cu.getImports().get(i).getNameAsString();
            if (name.startsWith(OLD_HTTP_SERVLET_PREFIX)) {
                cu.getImports().get(i).setName(name.replaceFirst(OLD_HTTP_SERVLET_PREFIX, NEW_HTTP_SERVLET_PREFIX));
                changed = true;
            }
        }

        return changed;
    }

    private static boolean rewriteServerConfigurationCalls(CompilationUnit cu) {
        boolean changed = false;
        boolean insertedConfig = false;

        for (MethodCallExpr call : cu.findAll(MethodCallExpr.class)) {
            String name = call.getNameAsString();
            if (!name.equals("setSendServerVersion") && !name.equals("setSendDateHeader")) {
                continue;
            }

            if (!insertedConfig) {
                insertHttpConfigurationDeclaration(call.findAncestor(BlockStmt.class).orElse(null), call.findAncestor(Statement.class).orElse(null));
                insertedConfig = true;
                changed = true;
            }

            call.setScope(new NameExpr("httpConfiguration"));
            changed = true;
        }

        return changed;
    }

    private static void insertHttpConfigurationDeclaration(BlockStmt block, Statement before) {
        if (block == null || before == null) {
            return;
        }
        boolean alreadyPresent = block.findAll(VariableDeclarator.class).stream()
                .anyMatch(v -> v.getNameAsString().equals("httpConfiguration"));
        if (alreadyPresent) {
            return;
        }
        int index = block.getStatements().indexOf(before);
        if (index >= 0) {
            block.addStatement(index, StaticJavaParser.parseStatement("HttpConfiguration httpConfiguration = new HttpConfiguration();"));
        }
    }

    private static boolean rewriteConnectorCreations(CompilationUnit cu) {
        boolean changed = false;

        for (ObjectCreationExpr creation : cu.findAll(ObjectCreationExpr.class)) {
            if (!isOldConnectorCreation(creation)) {
                continue;
            }

            Expression serverExpr = inferServerExpression(creation).orElse(new NameExpr("server"));
            creation.setType(new ClassOrInterfaceType(null, "ServerConnector"));
            creation.setArguments(NodeList.nodeList(
                    serverExpr,
                    new ObjectCreationExpr(null, new ClassOrInterfaceType(null, "HttpConnectionFactory"),
                            NodeList.nodeList(new NameExpr("httpConfiguration")))));
            changed = true;
        }

        return changed;
    }

    private static boolean isOldConnectorCreation(ObjectCreationExpr creation) {
        String typeName = creation.getType().getNameWithScope();
        return typeName.equals("SelectChannelConnector") || typeName.equals(OLD_CONNECTOR);
    }

    private static Optional<Expression> inferServerExpression(Node node) {
        return node.findAncestor(CompilationUnit.class).flatMap(Main::findServerExpression);
    }

    private static Optional<Expression> findServerExpression(CompilationUnit cu) {
        return cu.findAll(VariableDeclarator.class).stream()
                .filter(v -> isServerType(v.getTypeAsString()))
                .map(v -> (Expression) new NameExpr(v.getNameAsString()))
                .findFirst()
                .or(() -> cu.findAll(Parameter.class).stream()
                        .filter(p -> isServerType(p.getTypeAsString()))
                        .map(p -> (Expression) new NameExpr(p.getNameAsString()))
                        .findFirst());
    }

    private static boolean isServerType(String typeName) {
        return typeName.equals("Server") || typeName.equals(OLD_SERVER);
    }

    private static boolean usesHttpConfiguration(CompilationUnit cu) {
        return cu.findAll(MethodCallExpr.class).stream()
                .anyMatch(call -> call.getNameAsString().equals("setSendServerVersion") || call.getNameAsString().equals("setSendDateHeader"));
    }

    private static boolean usesHttpConnectionFactory(CompilationUnit cu) {
        return cu.findAll(ObjectCreationExpr.class).stream().anyMatch(Main::isOldConnectorCreation);
    }
}
