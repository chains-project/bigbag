package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.type.ClassOrInterfaceType;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

public class Main {

    private static final String OLD_CONNECTOR = "org.eclipse.jetty.server.nio.SelectChannelConnector";
    private static final String NEW_CONNECTOR = "ServerConnector";
    private static final String NEW_HTTP_CONFIGURATION = "HttpConfiguration";
    private static final String NEW_HTTP_CONNECTION_FACTORY = "HttpConnectionFactory";

    public static void main(String[] args) {
        final Path sourceRoot = args.length > 0 ? Paths.get(args[0]) : Paths.get(".");

        try (Stream<Path> paths = Files.walk(sourceRoot)) {
            paths.filter(path -> path.toString().endsWith(".java")).forEach(Main::transformFile);
        } catch (IOException e) {
            throw new RuntimeException("Failed to traverse source directory " + sourceRoot, e);
        }
    }

    private static void transformFile(Path path) {
        try {
            final CompilationUnit cu = StaticJavaParser.parse(path);
            if (transformCompilationUnit(cu)) {
                Files.write(path, cu.toString().getBytes(StandardCharsets.UTF_8));
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to transform " + path, e);
        }
    }

    private static boolean transformCompilationUnit(CompilationUnit cu) {
        boolean changed = false;

        changed |= rewriteImports(cu);

        final Set<String> connectorVariables = collectConnectorVariables(cu);
        changed |= rewriteConnectorDeclarations(cu, connectorVariables);
        changed |= rewriteJettyBlocks(cu, connectorVariables);
        changed |= rewriteImports(cu);

        return changed;
    }

    private static boolean rewriteImports(CompilationUnit cu) {
        boolean changed = false;

        for (int i = 0; i < cu.getImports().size(); i++) {
            final String name = cu.getImport(i).getNameAsString();
            if (name.startsWith("javax.servlet")) {
                cu.getImports().get(i).setName(name.replaceFirst("^javax", "jakarta"));
                changed = true;
            } else if (name.equals(OLD_CONNECTOR)) {
                cu.getImports().get(i).setName("org.eclipse.jetty.server." + NEW_CONNECTOR);
                changed = true;
            }
        }

        if (usesType(cu, NEW_CONNECTOR)) {
            changed |= ensureImport(cu, "org.eclipse.jetty.server." + NEW_CONNECTOR);
        }
        if (usesType(cu, NEW_HTTP_CONFIGURATION)) {
            changed |= ensureImport(cu, "org.eclipse.jetty.server." + NEW_HTTP_CONFIGURATION);
        }
        if (usesType(cu, NEW_HTTP_CONNECTION_FACTORY)) {
            changed |= ensureImport(cu, "org.eclipse.jetty.server." + NEW_HTTP_CONNECTION_FACTORY);
        }

        return changed;
    }

    private static boolean ensureImport(CompilationUnit cu, String fqcn) {
        if (cu.getImports().stream().anyMatch(importDecl -> importDecl.getNameAsString().equals(fqcn))) {
            return false;
        }
        cu.addImport(fqcn);
        return true;
    }

    private static boolean usesType(CompilationUnit cu, String simpleName) {
        return cu.findAll(ClassOrInterfaceType.class).stream()
                .anyMatch(type -> type.getNameAsString().equals(simpleName));
    }

    private static Set<String> collectConnectorVariables(CompilationUnit cu) {
        final Set<String> connectorVariables = new HashSet<>();

        for (VariableDeclarator declarator : cu.findAll(VariableDeclarator.class)) {
            if (declarator.getInitializer().isPresent() && isOldConnectorCreation(declarator.getInitializer().get())) {
                connectorVariables.add(declarator.getNameAsString());
            }
        }

        for (AssignExpr assignExpr : cu.findAll(AssignExpr.class)) {
            if (isOldConnectorCreation(assignExpr.getValue())) {
                extractAssignedName(assignExpr.getTarget()).ifPresent(connectorVariables::add);
            }
        }

        return connectorVariables;
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

    private static boolean rewriteConnectorDeclarations(CompilationUnit cu, Set<String> connectorVariables) {
        boolean changed = false;

        for (FieldDeclaration fieldDeclaration : cu.findAll(FieldDeclaration.class)) {
            if (shouldUpgradeConnectorType(fieldDeclaration.getVariables(), connectorVariables)) {
                for (VariableDeclarator variable : fieldDeclaration.getVariables()) {
                    variable.setType(new ClassOrInterfaceType(null, NEW_CONNECTOR));
                }
                changed = true;
            }
        }

        for (VariableDeclarator variableDeclarator : cu.findAll(VariableDeclarator.class)) {
            if ((connectorVariables.contains(variableDeclarator.getNameAsString())
                    || (variableDeclarator.getInitializer().isPresent() && isOldConnectorCreation(variableDeclarator.getInitializer().get())))
                    && variableDeclarator.getType().isClassOrInterfaceType()) {
                final ClassOrInterfaceType type = variableDeclarator.getType().asClassOrInterfaceType();
                if (type.getNameAsString().equals("Connector") || type.getNameAsString().equals("SelectChannelConnector")) {
                    variableDeclarator.setType(new ClassOrInterfaceType(null, NEW_CONNECTOR));
                    changed = true;
                }
            }
        }

        return changed;
    }

    private static boolean shouldUpgradeConnectorType(NodeList<VariableDeclarator> variables, Set<String> connectorVariables) {
        for (VariableDeclarator variable : variables) {
            if (connectorVariables.contains(variable.getNameAsString())
                    || (variable.getInitializer().isPresent() && isOldConnectorCreation(variable.getInitializer().get()))) {
                return true;
            }
        }
        return false;
    }

    private static boolean rewriteJettyBlocks(CompilationUnit cu, Set<String> connectorVariables) {
        boolean changed = false;

        for (ClassOrInterfaceDeclaration clazz : cu.findAll(ClassOrInterfaceDeclaration.class)) {
            final List<BlockStmt> blocks = clazz.findAll(BlockStmt.class);
            for (BlockStmt block : blocks) {
                changed |= rewriteBlock(block, clazz, connectorVariables);
            }
        }

        return changed;
    }

    private static boolean rewriteBlock(BlockStmt block, ClassOrInterfaceDeclaration owner, Set<String> connectorVariables) {
        boolean changed = false;

        final String serverReference = findServerReference(block, owner);
        if (!containsOldJettyPattern(block)) {
            return false;
        }
        final String httpConfigurationName = uniqueName(block, "httpConfiguration");
        final boolean needsHttpConfiguration = block.findAll(MethodCallExpr.class).stream()
                .anyMatch(Main::isOldServerConfigurationCall);

        int insertionIndex = -1;
        for (int i = 0; i < block.getStatements().size(); i++) {
            final Statement statement = block.getStatement(i);
            if (statement.findAll(MethodCallExpr.class).stream().anyMatch(Main::isOldServerConfigurationCall)
                    || statement.findAll(ObjectCreationExpr.class).stream().anyMatch(Main::isOldConnectorCreation)
                    || statement.findAll(AssignExpr.class).stream().anyMatch(expr -> isOldConnectorCreation(expr.getValue()))) {
                insertionIndex = i;
                break;
            }
        }

        if (needsHttpConfiguration && insertionIndex >= 0) {
            final CompilationUnit compilationUnit = block.findCompilationUnit().orElseThrow();
            ensureImport(compilationUnit, "org.eclipse.jetty.server." + NEW_HTTP_CONFIGURATION);
            ensureImport(compilationUnit, "org.eclipse.jetty.server." + NEW_HTTP_CONNECTION_FACTORY);
            block.addStatement(insertionIndex,
                    StaticJavaParser.parseStatement("final " + NEW_HTTP_CONFIGURATION + " " + httpConfigurationName + " = new " + NEW_HTTP_CONFIGURATION + "();"));
            changed = true;
        }

        for (MethodCallExpr callExpr : block.findAll(MethodCallExpr.class)) {
            if (isOldServerConfigurationCall(callExpr)) {
                callExpr.setScope(new NameExpr(httpConfigurationName));
                changed = true;
            }
        }

        for (ObjectCreationExpr objectCreationExpr : block.findAll(ObjectCreationExpr.class)) {
            if (isOldConnectorCreation(objectCreationExpr)) {
                final NodeList<Expression> arguments = new NodeList<>();
                arguments.add(StaticJavaParser.parseExpression(serverReference));
                if (needsHttpConfiguration) {
                    arguments.add(new ObjectCreationExpr(null,
                            new ClassOrInterfaceType(null, NEW_HTTP_CONNECTION_FACTORY),
                            NodeList.nodeList(new NameExpr(httpConfigurationName))));
                }

                objectCreationExpr.setType(new ClassOrInterfaceType(null, NEW_CONNECTOR));
                objectCreationExpr.setArguments(arguments);
                changed = true;
            }
        }

        for (AssignExpr assignExpr : block.findAll(AssignExpr.class)) {
            if (isOldConnectorCreation(assignExpr.getValue())) {
                changed |= upgradeAssignmentTargetType(assignExpr, connectorVariables);
            }
        }

        return changed;
    }

    private static boolean upgradeAssignmentTargetType(AssignExpr assignExpr, Set<String> connectorVariables) {
        return extractAssignedName(assignExpr.getTarget())
                .filter(connectorVariables::contains)
                .isPresent();
    }

    private static boolean isOldServerConfigurationCall(MethodCallExpr methodCallExpr) {
        final String name = methodCallExpr.getNameAsString();
        return name.equals("setSendServerVersion") || name.equals("setSendDateHeader");
    }

    private static boolean isOldConnectorCreation(Expression expression) {
        return expression.isObjectCreationExpr()
                && expression.asObjectCreationExpr().getType().getNameAsString().equals("SelectChannelConnector");
    }

    private static String findServerReference(BlockStmt block, ClassOrInterfaceDeclaration owner) {
        for (MethodDeclaration methodDeclaration : owner.getMethods()) {
            if (methodDeclaration.getBody().filter(block::equals).isPresent()) {
                final Optional<String> parameterName = methodDeclaration.getParameters().stream()
                        .filter(parameter -> parameter.getType().isClassOrInterfaceType())
                        .filter(parameter -> parameter.getType().asClassOrInterfaceType().getNameAsString().equals("Server"))
                        .map(parameter -> parameter.getNameAsString())
                        .findFirst();
                if (parameterName.isPresent()) {
                    return parameterName.get();
                }
            }
        }

        for (ConstructorDeclaration constructorDeclaration : owner.getConstructors()) {
            if (constructorDeclaration.getBody().equals(block)) {
                final Optional<String> parameterName = constructorDeclaration.getParameters().stream()
                        .filter(parameter -> parameter.getType().isClassOrInterfaceType())
                        .filter(parameter -> parameter.getType().asClassOrInterfaceType().getNameAsString().equals("Server"))
                        .map(parameter -> parameter.getNameAsString())
                        .findFirst();
                if (parameterName.isPresent()) {
                    return parameterName.get();
                }
            }
        }

        return owner.findAll(FieldDeclaration.class).stream()
                .flatMap(fieldDeclaration -> fieldDeclaration.getVariables().stream())
                .filter(variableDeclarator -> variableDeclarator.getType().isClassOrInterfaceType())
                .filter(variableDeclarator -> variableDeclarator.getType().asClassOrInterfaceType().getNameAsString().equals("Server"))
                .map(variableDeclarator -> variableDeclarator.getNameAsString())
                .findFirst()
                .map(name -> "this." + name)
                .orElse("server");
    }

    private static String uniqueName(BlockStmt block, String baseName) {
        String candidate = baseName;
        int counter = 1;
        final Set<String> existingNames = new HashSet<>();
        block.findAll(VariableDeclarator.class).forEach(variableDeclarator -> existingNames.add(variableDeclarator.getNameAsString()));
        block.findAll(MethodCallExpr.class).forEach(methodCallExpr -> existingNames.add(methodCallExpr.getNameAsString()));
        while (existingNames.contains(candidate)) {
            candidate = baseName + counter++;
        }
        return candidate;
    }

    private static boolean containsOldJettyPattern(BlockStmt block) {
        return block.findAll(MethodCallExpr.class).stream().anyMatch(Main::isOldServerConfigurationCall)
                || block.findAll(ObjectCreationExpr.class).stream().anyMatch(Main::isOldConnectorCreation)
                || block.findAll(VariableDeclarator.class).stream().anyMatch(variable -> {
                    if (!variable.getInitializer().isPresent() || !variable.getType().isClassOrInterfaceType()) {
                        return false;
                    }
                    final String typeName = variable.getType().asClassOrInterfaceType().getNameAsString();
                    return (typeName.equals("Connector") || typeName.equals("SelectChannelConnector"))
                            && isOldConnectorCreation(variable.getInitializer().get());
                });
    }
}
