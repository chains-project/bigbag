package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.utils.SourceRoot;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Main {
    private static final String JETTY_SERVER = "org.eclipse.jetty.server.Server";
    private static final String JETTY_CONNECTOR = "org.eclipse.jetty.server.Connector";
    private static final String JETTY_NETWORK_CONNECTOR = "org.eclipse.jetty.server.NetworkConnector";
    private static final String JETTY_SELECT_CHANNEL_CONNECTOR = "org.eclipse.jetty.server.nio.SelectChannelConnector";
    private static final String JETTY_SERVER_CONNECTOR = "org.eclipse.jetty.server.ServerConnector";
    private static final String JETTY_HTTP_CONFIGURATION = "org.eclipse.jetty.server.HttpConfiguration";
    private static final String JETTY_HTTP_CONNECTION_FACTORY = "org.eclipse.jetty.server.HttpConnectionFactory";

    private static final String JAVAX_SERVLET = "javax.servlet";
    private static final String JAKARTA_SERVLET = "jakarta.servlet";

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            throw new IllegalArgumentException("Usage: Main <source-root>");
        }

        Path root = Paths.get(args[0]);
        if (!Files.isDirectory(root)) {
            throw new IllegalArgumentException("Source root does not exist: " + root);
        }

        for (Path sourceRootPath : findSourceRoots(root)) {
            SourceRoot sourceRoot = new SourceRoot(sourceRootPath);
            sourceRoot.tryToParseParallelized();
            for (CompilationUnit compilationUnit : sourceRoot.getCompilationUnits()) {
                if (transform(compilationUnit)) {
                    compilationUnit.getStorage().ifPresent(storage -> storage.save());
                }
            }
        }
    }

    private static List<Path> findSourceRoots(Path root) throws IOException {
        try (Stream<Path> stream = Files.walk(root)) {
            return stream
                    .filter(Files::isDirectory)
                    .filter(path -> path.toString().endsWith("src/main/java") || path.toString().endsWith("src/test/java"))
                    .collect(Collectors.toList());
        }
    }

    private static boolean transform(CompilationUnit cu) {
        boolean changed = false;

        changed |= rewriteImports(cu);
        changed |= rewriteJakartaTypes(cu);
        changed |= rewriteJettyConnectorPattern(cu);

        return changed;
    }

    private static boolean rewriteImports(CompilationUnit cu) {
        boolean changed = false;

        List<String> toAdd = new ArrayList<>();
        Set<String> needed = new HashSet<>();

        for (var it = cu.getImports().iterator(); it.hasNext(); ) {
            var imp = it.next();
            String name = imp.getNameAsString();
            if (name.equals(JETTY_SELECT_CHANNEL_CONNECTOR)) {
                it.remove();
                changed = true;
                needed.add(JETTY_SERVER_CONNECTOR);
                needed.add(JETTY_NETWORK_CONNECTOR);
                needed.add(JETTY_HTTP_CONFIGURATION);
                needed.add(JETTY_HTTP_CONNECTION_FACTORY);
            } else if (name.startsWith(JAVAX_SERVLET)) {
                imp.setName(name.replaceFirst(JAVAX_SERVLET, JAKARTA_SERVLET));
                changed = true;
            }
        }

        for (String name : needed) {
            if (!cu.getImports().stream().anyMatch(i -> i.getNameAsString().equals(name))) {
                toAdd.add(name);
            }
        }

        for (String name : toAdd) {
            cu.addImport(name);
            changed = true;
        }

        return changed;
    }

    private static boolean rewriteJakartaTypes(CompilationUnit cu) {
        boolean changed = false;

        for (ClassOrInterfaceType type : cu.findAll(ClassOrInterfaceType.class)) {
            if (type.getNameWithScope().startsWith(JAVAX_SERVLET)) {
                type.setName(type.getNameWithScope().replaceFirst(JAVAX_SERVLET, JAKARTA_SERVLET));
                changed = true;
            }
        }

        return changed;
    }

    private static boolean rewriteJettyConnectorPattern(CompilationUnit cu) {
        boolean changed = false;

        for (ConstructorDeclaration constructor : cu.findAll(ConstructorDeclaration.class)) {
            if (constructor.getBody() != null) {
                changed |= rewriteBlock(constructor.getBody());
            }
        }

        for (MethodDeclaration method : cu.findAll(MethodDeclaration.class)) {
            if (method.getBody().isEmpty()) {
                continue;
            }
            changed |= rewriteBlock(method.getBody().get());
        }

        for (ClassOrInterfaceDeclaration type : cu.findAll(ClassOrInterfaceDeclaration.class)) {
            for (FieldDeclaration field : type.getFields()) {
                changed |= rewriteField(field);
            }
        }

        return changed;
    }

    private static boolean rewriteField(FieldDeclaration field) {
        boolean changed = false;
        for (VariableDeclarator variable : field.getVariables()) {
            changed |= rewriteVariableDeclarator(variable);
        }
        return changed;
    }

    private static boolean rewriteBlock(BlockStmt block) {
        boolean changed = false;
        List<Statement> statements = new ArrayList<>(block.getStatements());

        for (int i = 0; i < statements.size(); i++) {
            Statement statement = statements.get(i);
            changed |= rewriteStatement(statement);

            Optional<MethodCallExpr> sendVersion = statement.findFirst(MethodCallExpr.class,
                    m -> isJettyHeaderSetter(m, "setSendServerVersion") || isJettyHeaderSetter(m, "setSendDateHeader"));
            if (!sendVersion.isPresent()) {
                continue;
            }

            String configurationName = uniqueName(block, "httpConfiguration");
            if (!containsName(block, configurationName)) {
                BlockStmt parent = block;
                Statement declaration = StaticJavaParser.parseStatement(
                        "org.eclipse.jetty.server.HttpConfiguration " + configurationName + " = new org.eclipse.jetty.server.HttpConfiguration();");
                parent.addStatement(i, declaration);
                i++;
                changed = true;
            }

            changed |= replaceHeaderSetterScope(block, configurationName);
            changed |= rewriteConnectorCreations(block, configurationName);
        }

        return changed;
    }

    private static boolean rewriteStatement(Statement statement) {
        boolean changed = false;
        changed |= statement.findAll(VariableDeclarator.class).stream().anyMatch(Main::rewriteVariableDeclarator);
        changed |= statement.findAll(ObjectCreationExpr.class).stream().anyMatch(Main::rewriteObjectCreation);
        return changed;
    }

    private static boolean rewriteVariableDeclarator(VariableDeclarator variable) {
        boolean changed = false;

        if (isType(variable.getType(), JETTY_CONNECTOR) || isType(variable.getType(), JETTY_SELECT_CHANNEL_CONNECTOR)) {
            variable.setType(JETTY_NETWORK_CONNECTOR);
            changed = true;
        }

        if (variable.getInitializer().isPresent()) {
            Expression initializer = variable.getInitializer().get();
            if (initializer.isObjectCreationExpr() && rewriteObjectCreation(initializer.asObjectCreationExpr())) {
                changed = true;
            }
        }

        return changed;
    }

    private static boolean rewriteObjectCreation(ObjectCreationExpr creation) {
        if (!isType(creation.getType(), JETTY_SELECT_CHANNEL_CONNECTOR)) {
            return false;
        }

        Optional<Expression> serverExpression = findServerExpression(creation);
        if (!serverExpression.isPresent()) {
            return false;
        }

        Optional<String> configName = findHttpConfigurationName(creation);
        if (configName.isPresent()) {
            creation.setType(JETTY_SERVER_CONNECTOR);
            creation.setArguments(NodeList.nodeList(serverExpression.get(), new ObjectCreationExpr(null,
                    StaticJavaParser.parseClassOrInterfaceType(JETTY_HTTP_CONNECTION_FACTORY),
                    NodeList.nodeList(new NameExpr(configName.get())))));
            return true;
        }

        creation.setType(JETTY_SERVER_CONNECTOR);
        creation.setArguments(NodeList.nodeList(serverExpression.get()));
        return true;
    }

    private static boolean rewriteConnectorCreations(BlockStmt block, String configurationName) {
        boolean changed = false;
        for (ObjectCreationExpr creation : block.findAll(ObjectCreationExpr.class)) {
            if (!isType(creation.getType(), JETTY_SELECT_CHANNEL_CONNECTOR)) {
                continue;
            }
            Optional<Expression> serverExpression = findServerExpression(creation);
            if (!serverExpression.isPresent()) {
                continue;
            }

            creation.setType(JETTY_SERVER_CONNECTOR);
            creation.setArguments(NodeList.nodeList(serverExpression.get(), new ObjectCreationExpr(null,
                    StaticJavaParser.parseClassOrInterfaceType(JETTY_HTTP_CONNECTION_FACTORY),
                    NodeList.nodeList(new NameExpr(configurationName)))));
            changed = true;
        }
        return changed;
    }

    private static boolean replaceHeaderSetterScope(BlockStmt block, String configurationName) {
        boolean changed = false;
        for (MethodCallExpr call : block.findAll(MethodCallExpr.class)) {
            if (!isJettyHeaderSetter(call, "setSendServerVersion") && !isJettyHeaderSetter(call, "setSendDateHeader")) {
                continue;
            }
            call.setScope(new NameExpr(configurationName));
            changed = true;
        }
        return changed;
    }

    private static boolean isJettyHeaderSetter(MethodCallExpr call, String name) {
        return call.getNameAsString().equals(name)
                && call.getScope().isPresent()
                && resolvesTo(call.getScope().get(), JETTY_SERVER);
    }

    private static Optional<Expression> findServerExpression(Node node) {
        for (Node current = node; current != null; current = current.getParentNode().orElse(null)) {
            if (current instanceof BlockStmt) {
                BlockStmt block = (BlockStmt) current;
                for (Statement statement : block.getStatements()) {
                    Optional<Expression> candidate = statement.findFirst(VariableDeclarator.class,
                            variable -> isType(variable.getType(), JETTY_SERVER))
                            .map(VariableDeclarator::getNameAsExpression);
                    if (candidate.isPresent()) {
                        return candidate;
                    }
                }
            }

            if (current instanceof ClassOrInterfaceDeclaration) {
                ClassOrInterfaceDeclaration declaration = (ClassOrInterfaceDeclaration) current;
                for (FieldDeclaration field : declaration.getFields()) {
                    for (VariableDeclarator variable : field.getVariables()) {
                        if (isType(variable.getType(), JETTY_SERVER)) {
                            return Optional.of(variable.getNameAsExpression());
                        }
                    }
                }
            }

            if (current instanceof MethodDeclaration) {
                MethodDeclaration method = (MethodDeclaration) current;
                for (Parameter parameter : method.getParameters()) {
                    if (isType(parameter.getType(), JETTY_SERVER)) {
                        return Optional.of(parameter.getNameAsExpression());
                    }
                }
            }
        }
        return Optional.empty();
    }

    private static Optional<String> findHttpConfigurationName(Node node) {
        for (Node current = node; current != null; current = current.getParentNode().orElse(null)) {
            if (current instanceof BlockStmt) {
                BlockStmt block = (BlockStmt) current;
                for (Statement statement : block.getStatements()) {
                    Optional<VariableDeclarator> variable = statement.findFirst(VariableDeclarator.class,
                            v -> isType(v.getType(), JETTY_HTTP_CONFIGURATION));
                    if (variable.isPresent()) {
                        return Optional.of(variable.get().getNameAsString());
                    }
                }
            }
        }
        return Optional.empty();
    }

    private static boolean containsName(BlockStmt block, String name) {
        return block.findAll(VariableDeclarator.class).stream().anyMatch(v -> v.getNameAsString().equals(name));
    }

    private static String uniqueName(BlockStmt block, String base) {
        String candidate = base;
        int index = 1;
        while (containsName(block, candidate)) {
            candidate = base + index++;
        }
        return candidate;
    }

    private static boolean isType(Type type, String fullyQualifiedName) {
        if (type.isClassOrInterfaceType()) {
            String name = type.asClassOrInterfaceType().getNameWithScope();
            return name.equals(simpleName(fullyQualifiedName)) || name.equals(fullyQualifiedName);
        }
        return false;
    }

    private static boolean resolvesTo(Expression expression, String fullyQualifiedName) {
        if (expression.isNameExpr()) {
            return expression.asNameExpr().getNameAsString().equals(simpleName(fullyQualifiedName));
        }
        return false;
    }

    private static String simpleName(String fullyQualifiedName) {
        return fullyQualifiedName.substring(fullyQualifiedName.lastIndexOf('.') + 1);
    }
}
