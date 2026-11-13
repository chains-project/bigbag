package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.CallableDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.Statement;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public class Main {

    private static final String HTTP_CONFIGURATION_FQCN = "org.eclipse.jetty.server.HttpConfiguration";
    private static final String HTTP_CONNECTION_FACTORY_FQCN = "org.eclipse.jetty.server.HttpConnectionFactory";
    private static final String SERVER_CONNECTOR_FQCN = "org.eclipse.jetty.server.ServerConnector";

    public static void main(String[] args) {
        if (args.length == 0) {
            System.err.println("Usage: Main <source-root>");
            System.exit(1);
        }

        Path root = Paths.get(args[0]);
        JavaParser parser = new JavaParser(new ParserConfiguration());

        try {
            List<Path> javaFiles = new ArrayList<>();
            Files.walk(root)
                    .filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                    .forEach(javaFiles::add);

            javaFiles.sort(Comparator.naturalOrder());

            for (Path path : javaFiles) {
                Optional<CompilationUnit> maybeCu = parser.parse(path).getResult();
                if (maybeCu.isEmpty()) {
                    continue;
                }

                CompilationUnit cu = maybeCu.get();
                boolean changed = transformCompilationUnit(cu);
                if (changed) {
                    Files.writeString(path, cu.toString());
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static boolean transformCompilationUnit(CompilationUnit cu) {
        boolean changed = false;

        List<CallableDeclaration<?>> callables = new ArrayList<>();
        callables.addAll(cu.findAll(MethodDeclaration.class));
        callables.addAll(cu.findAll(ConstructorDeclaration.class));

        for (CallableDeclaration<?> callable : callables) {
            Optional<BlockStmt> maybeBody = getBody(callable);
            if (maybeBody.isEmpty()) {
                continue;
            }

            changed |= transformBody(cu, maybeBody.get());
        }

        if (changed) {
            addImportIfNeeded(cu, HTTP_CONFIGURATION_FQCN);
            addImportIfNeeded(cu, HTTP_CONNECTION_FACTORY_FQCN);
            addImportIfNeeded(cu, SERVER_CONNECTOR_FQCN);
            removeImportIfPresent(cu, "org.eclipse.jetty.server.nio.SelectChannelConnector");
        }

        return changed;
    }

    private static Optional<BlockStmt> getBody(CallableDeclaration<?> callable) {
        if (callable instanceof MethodDeclaration) {
            return ((MethodDeclaration) callable).getBody();
        }
        if (callable instanceof ConstructorDeclaration) {
            return Optional.of(((ConstructorDeclaration) callable).getBody());
        }
        return Optional.empty();
    }

    private static boolean transformBody(CompilationUnit cu, BlockStmt body) {
        boolean changed = false;

        List<MethodCallExpr> removedServerSettings = body.findAll(MethodCallExpr.class, Main::isRemovedServerSetting);
        String configName = null;
        if (!removedServerSettings.isEmpty()) {
            configName = uniqueLocalName(body, "httpConfiguration");
            insertHttpConfiguration(body, configName);
            changed = true;

            for (MethodCallExpr call : removedServerSettings) {
                call.setScope(new NameExpr(configName));
            }
        }

        List<ObjectCreationExpr> connectors = body.findAll(ObjectCreationExpr.class,
                expr -> expr.getType().asString().equals("SelectChannelConnector"));
        for (ObjectCreationExpr creation : connectors) {
            Optional<Expression> serverExpr = findServerReference(body, creation);
            if (serverExpr.isEmpty()) {
                continue;
            }

            String replacementSource;
            if (configName != null) {
                replacementSource = "new ServerConnector(" + serverExpr.get() + ", new HttpConnectionFactory(" + configName + "))";
            } else {
                replacementSource = "new ServerConnector(" + serverExpr.get() + ")";
            }

            Expression replacement = StaticJavaParser.parseExpression(replacementSource);
            creation.replace(replacement);
            updateConnectorVariableType(creation, SERVER_CONNECTOR_FQCN);
            changed = true;
        }

        return changed;
    }

    private static boolean isRemovedServerSetting(MethodCallExpr call) {
        String name = call.getNameAsString();
        return "setSendServerVersion".equals(name) || "setSendDateHeader".equals(name);
    }

    private static Optional<Expression> findServerReference(BlockStmt body, ObjectCreationExpr creation) {
        Optional<Node> creationParent = creation.getParentNode();
        if (creationParent.isEmpty()) {
            return Optional.empty();
        }

        List<ObjectCreationExpr> serverCreations = body.findAll(ObjectCreationExpr.class,
                expr -> expr.getType().asString().equals("Server"));
        ObjectCreationExpr bestMatch = null;
        for (ObjectCreationExpr serverCreation : serverCreations) {
            if (!serverCreation.getBegin().isPresent() || !creation.getBegin().isPresent()) {
                continue;
            }
            if (serverCreation.getBegin().get().isAfter(creation.getBegin().get())) {
                continue;
            }
            if (bestMatch == null || serverCreation.getBegin().get().isAfter(bestMatch.getBegin().get())) {
                bestMatch = serverCreation;
            }
        }

        if (bestMatch != null) {
            Optional<Expression> target = findAssignedTarget(bestMatch);
            if (target.isPresent()) {
                return target;
            }
        }

        return Optional.empty();
    }

    private static Optional<String> findConnectorReference(ObjectCreationExpr creation) {
        Optional<Node> parent = creation.getParentNode();
        if (parent.isEmpty()) {
            return Optional.empty();
        }

        Node node = parent.get();
        if (node instanceof VariableDeclarator) {
            return Optional.of(((VariableDeclarator) node).getNameAsString());
        }
        if (node instanceof AssignExpr) {
            return Optional.of(normalizeReference(((AssignExpr) node).getTarget()));
        }

        return Optional.empty();
    }

    private static Optional<Expression> findAssignedTarget(ObjectCreationExpr creation) {
        Optional<Node> parent = creation.getParentNode();
        if (parent.isEmpty()) {
            return Optional.empty();
        }

        Node node = parent.get();
        if (node instanceof VariableDeclarator) {
            return Optional.of(new NameExpr(((VariableDeclarator) node).getNameAsString()));
        }
        if (node instanceof AssignExpr) {
            return Optional.of(((AssignExpr) node).getTarget().clone());
        }

        return Optional.empty();
    }

    private static void updateConnectorVariableType(ObjectCreationExpr creation, String targetType) {
        Optional<Node> parent = creation.getParentNode();
        if (parent.isEmpty() || !(parent.get() instanceof VariableDeclarator)) {
            return;
        }

        VariableDeclarator declarator = (VariableDeclarator) parent.get();
        declarator.setType(StaticJavaParser.parseClassOrInterfaceType(targetType.substring(targetType.lastIndexOf('.') + 1)));
    }

    private static String normalizeReference(Expression expression) {
        if (expression instanceof NameExpr) {
            return ((NameExpr) expression).getNameAsString();
        }
        if (expression instanceof FieldAccessExpr) {
            return expression.toString();
        }
        return expression.toString();
    }

    private static void insertHttpConfiguration(BlockStmt body, String configName) {
        Statement statement = StaticJavaParser.parseStatement(
                HTTP_CONFIGURATION_FQCN.substring(HTTP_CONFIGURATION_FQCN.lastIndexOf('.') + 1)
                        + " " + configName + " = new "
                        + HTTP_CONFIGURATION_FQCN.substring(HTTP_CONFIGURATION_FQCN.lastIndexOf('.') + 1)
                        + "();");

        List<Statement> statements = body.getStatements();
        int index = 0;
        if (!statements.isEmpty() && statements.get(0).isExplicitConstructorInvocationStmt()) {
            index = 1;
        }
        body.addStatement(index, statement);
    }

    private static String uniqueLocalName(BlockStmt body, String baseName) {
        String candidate = baseName;
        int counter = 1;
        while (hasNameConflict(body, candidate)) {
            candidate = baseName + counter;
            counter++;
        }
        return candidate;
    }

    private static boolean hasNameConflict(BlockStmt body, String name) {
        return body.findAll(NameExpr.class, expr -> expr.getNameAsString().equals(name)).size() > 0
                || body.findAll(VariableDeclarator.class, variable -> variable.getNameAsString().equals(name)).size() > 0;
    }

    private static void addImportIfNeeded(CompilationUnit cu, String fqcn) {
        if (!cu.getImports().stream().anyMatch(importDecl -> importDecl.getNameAsString().equals(fqcn))) {
            cu.addImport(fqcn);
        }
    }

    private static void removeImportIfPresent(CompilationUnit cu, String fqcn) {
        cu.getImports().removeIf(importDecl -> importDecl.getNameAsString().equals(fqcn));
    }
}
