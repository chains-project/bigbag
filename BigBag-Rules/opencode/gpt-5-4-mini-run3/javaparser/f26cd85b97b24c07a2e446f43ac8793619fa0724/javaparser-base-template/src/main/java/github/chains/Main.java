package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.ThisExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.Optional;

public class Main {
    private static final String OLD_CONNECTOR_FQCN = "org.eclipse.jetty.server.nio.SelectChannelConnector";
    private static final String NEW_CONNECTOR_FQCN = "org.eclipse.jetty.server.ServerConnector";
    private static final String SERVER_FQCN = "org.eclipse.jetty.server.Server";

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            throw new IllegalArgumentException("Usage: Main <source-root>");
        }

        Path root = Paths.get(args[0]);
        if (!Files.isDirectory(root)) {
            throw new IllegalArgumentException("Not a directory: " + root);
        }

        try (var paths = Files.walk(root)) {
            paths.filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                    .sorted(Comparator.naturalOrder())
                    .forEach(Main::rewriteJavaFile);
        }
    }

    private static void rewriteJavaFile(Path path) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(path);
            LexicalPreservingPrinter.setup(cu);

            boolean changed = false;
            boolean touchedConnector = false;
            touchedConnector |= replaceOldTypeReferences(cu);
            touchedConnector |= replaceOldConstructorCalls(cu);
            changed |= replaceImports(cu, touchedConnector);

            if (changed) {
                Files.writeString(path, LexicalPreservingPrinter.print(cu), StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to rewrite " + path, e);
        }
    }

    private static boolean replaceImports(CompilationUnit cu, boolean shouldAddNewImport) {
        boolean changed = false;
        if (cu.getImports().removeIf(importDeclaration -> importDeclaration.getNameAsString().equals(OLD_CONNECTOR_FQCN))) {
            changed = true;
        }
        boolean hasNewImport = cu.getImports().stream()
                .anyMatch(importDeclaration -> importDeclaration.getNameAsString().equals(NEW_CONNECTOR_FQCN));
        if (shouldAddNewImport && !hasNewImport) {
            cu.addImport(NEW_CONNECTOR_FQCN);
            changed = true;
        }
        return changed;
    }

    private static boolean replaceOldTypeReferences(CompilationUnit cu) {
        boolean changed = false;
        for (ClassOrInterfaceType type : cu.findAll(ClassOrInterfaceType.class)) {
            if (type.getParentNode().filter(parent -> parent instanceof ObjectCreationExpr).isPresent()) {
                continue;
            }
            if (type.asString().equals(OLD_CONNECTOR_FQCN) || type.asString().equals("SelectChannelConnector")) {
                type.replace(StaticJavaParser.parseClassOrInterfaceType(NEW_CONNECTOR_FQCN));
                changed = true;
            }
        }
        return changed;
    }

    private static boolean replaceOldConstructorCalls(CompilationUnit cu) {
        boolean changed = false;
        for (ObjectCreationExpr creation : cu.findAll(ObjectCreationExpr.class)) {
            if (isConnectorCreationNeedingArgs(creation)) {
                Optional<Expression> serverExpr = findServerExpression(creation);
                if (serverExpr.isPresent()) {
                    if (isOldConnectorCreation(creation)) {
                        creation.setType(StaticJavaParser.parseClassOrInterfaceType(NEW_CONNECTOR_FQCN));
                    }
                    creation.getArguments().clear();
                    creation.addArgument(serverExpr.get());
                    changed = true;
                }
            }
        }
        return changed;
    }

    private static boolean isOldConnectorCreation(ObjectCreationExpr creation) {
        String typeName = creation.getType().asString();
        return typeName.equals("SelectChannelConnector") || typeName.equals(OLD_CONNECTOR_FQCN);
    }

    private static boolean isConnectorCreationNeedingArgs(ObjectCreationExpr creation) {
        String typeName = creation.getType().asString();
        return isOldConnectorCreation(creation)
                || typeName.equals("ServerConnector")
                || typeName.equals(NEW_CONNECTOR_FQCN);
    }

    private static Optional<Expression> findServerExpression(Node node) {
        Optional<Expression> localOrParameter = findNearestLocalOrParameterServer(node);
        if (localOrParameter.isPresent()) {
            return localOrParameter;
        }

        Optional<Expression> field = findFieldServerExpression(node);
        if (field.isPresent()) {
            return field;
        }

        return Optional.empty();
    }

    private static Optional<Expression> findNearestLocalOrParameterServer(Node node) {
        Optional<Statement> statement = node.findAncestor(Statement.class);
        if (statement.isPresent()) {
            Optional<BlockStmt> block = statement.get().findAncestor(BlockStmt.class);
            if (block.isPresent()) {
                for (Statement sibling : block.get().getStatements()) {
                    if (sibling == statement.get()) {
                        break;
                    }
                    Optional<Expression> candidate = serverExpressionFromStatement(sibling);
                    if (candidate.isPresent()) {
                        return candidate;
                    }
                }
            }
        }

        Optional<MethodDeclaration> method = node.findAncestor(MethodDeclaration.class);
        if (method.isPresent()) {
            for (Parameter parameter : method.get().getParameters()) {
                if (isServerType(parameter.getType().asString())) {
                    return Optional.of(new NameExpr(parameter.getNameAsString()));
                }
            }
        }

        Optional<ConstructorDeclaration> constructor = node.findAncestor(ConstructorDeclaration.class);
        if (constructor.isPresent()) {
            for (Parameter parameter : constructor.get().getParameters()) {
                if (isServerType(parameter.getType().asString())) {
                    return Optional.of(new NameExpr(parameter.getNameAsString()));
                }
            }
        }

        return Optional.empty();
    }

    private static Optional<Expression> serverExpressionFromStatement(Statement statement) {
        for (var variableDeclaration : statement.findAll(com.github.javaparser.ast.expr.VariableDeclarationExpr.class)) {
            for (VariableDeclarator variable : variableDeclaration.getVariables()) {
                if (isServerType(variable.getType().asString())) {
                    return Optional.of(new NameExpr(variable.getNameAsString()));
                }
            }
        }
        return Optional.empty();
    }

    private static Optional<Expression> findFieldServerExpression(Node node) {
        Optional<ClassOrInterfaceDeclaration> classDeclaration = node.findAncestor(ClassOrInterfaceDeclaration.class);
        if (classDeclaration.isEmpty()) {
            return Optional.empty();
        }

        for (FieldDeclaration field : classDeclaration.get().getFields()) {
            if (field.isStatic()) {
                for (VariableDeclarator variable : field.getVariables()) {
                    if (isServerType(variable.getType().asString())) {
                        return Optional.of(new NameExpr(variable.getNameAsString()));
                    }
                }
            } else {
                for (VariableDeclarator variable : field.getVariables()) {
                    if (isServerType(variable.getType().asString())) {
                        return Optional.of(new FieldAccessExpr(new ThisExpr(), variable.getNameAsString()));
                    }
                }
            }
        }

        return Optional.empty();
    }

    private static boolean isServerType(String typeName) {
        return typeName.equals("Server") || typeName.equals(SERVER_FQCN);
    }
}
