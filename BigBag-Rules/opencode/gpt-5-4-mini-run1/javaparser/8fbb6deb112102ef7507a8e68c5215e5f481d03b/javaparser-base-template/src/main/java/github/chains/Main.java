package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class Main {

    private static final String OLD_TYPE = "SelectChannelConnector";
    private static final String NEW_TYPE = "ServerConnector";
    private static final String OLD_IMPORT = "org.eclipse.jetty.server.nio.SelectChannelConnector";
    private static final String NEW_IMPORT = "org.eclipse.jetty.server.ServerConnector";
    private static final String OLD_SERVLET_PREFIX = "javax.servlet";
    private static final String NEW_SERVLET_PREFIX = "jakarta.servlet";

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            throw new IllegalArgumentException("Expected a single source directory argument");
        }

        Path sourceRoot = Paths.get(args[0]);
        List<Path> javaFiles = new ArrayList<>();
        try (var paths = Files.walk(sourceRoot)) {
            paths.filter(path -> path.toString().endsWith(".java")).forEach(javaFiles::add);
        }

        for (Path javaFile : javaFiles) {
            transform(javaFile);
        }
    }

    private static void transform(Path javaFile) throws IOException {
        CompilationUnit cu = StaticJavaParser.parse(javaFile);
        LexicalPreservingPrinter.setup(cu);

        Map<String, Expression> connectorOwners = collectConnectorOwners(cu);
        boolean changed = false;

        for (ClassOrInterfaceType type : cu.findAll(ClassOrInterfaceType.class)) {
            if (isOldType(type.getNameAsString())) {
                type.setName(NEW_TYPE);
                changed = true;
            }
        }

        for (ObjectCreationExpr creation : cu.findAll(ObjectCreationExpr.class)) {
            if (!isOldType(creation.getType().getNameAsString())) {
                continue;
            }

            Optional<Expression> owner = inferOwnerExpression(creation, connectorOwners);
            creation.setType(NEW_TYPE);
            if (owner.isPresent()) {
                creation.setArguments(NodeList.nodeList(owner.get().clone()));
            }
            changed = true;
        }

        if (!changed) {
            return;
        }

        cu.getImports().replaceAll(importDeclaration -> {
            String name = importDeclaration.getNameAsString();
            if (name.equals(OLD_IMPORT)) {
                return new com.github.javaparser.ast.ImportDeclaration(NEW_IMPORT, false, false);
            }
            if (name.startsWith(OLD_SERVLET_PREFIX)) {
                return new com.github.javaparser.ast.ImportDeclaration(NEW_SERVLET_PREFIX + name.substring(OLD_SERVLET_PREFIX.length()), false, false);
            }
            return importDeclaration;
        });
        if (cu.getImports().stream().noneMatch(importDeclaration -> importDeclaration.getNameAsString().equals(NEW_IMPORT))) {
            cu.addImport(NEW_IMPORT);
        }

        Files.writeString(javaFile, LexicalPreservingPrinter.print(cu), StandardCharsets.UTF_8);
    }

    private static Map<String, Expression> collectConnectorOwners(CompilationUnit cu) {
        Map<String, Expression> owners = new HashMap<>();

        for (MethodCallExpr call : cu.findAll(MethodCallExpr.class)) {
            if (!"addConnector".equals(call.getNameAsString()) || call.getArguments().size() != 1 || call.getScope().isEmpty()) {
                continue;
            }

            Expression argument = call.getArgument(0);
            if (argument.isNameExpr() || argument.isFieldAccessExpr()) {
                for (String key : referenceKeys(argument)) {
                    owners.putIfAbsent(key, call.getScope().get().clone());
                }
            }
        }

        return owners;
    }

    private static Optional<Expression> inferOwnerExpression(ObjectCreationExpr creation, Map<String, Expression> connectorOwners) {
        Optional<MethodCallExpr> addConnectorCall = creation.findAncestor(MethodCallExpr.class)
                .filter(call -> "addConnector".equals(call.getNameAsString()) && call.getArguments().contains(creation));
        if (addConnectorCall.isPresent() && addConnectorCall.get().getScope().isPresent()) {
            return addConnectorCall.get().getScope();
        }

        Optional<VariableDeclarator> variableDeclarator = creation.findAncestor(VariableDeclarator.class);
        if (variableDeclarator.isPresent() && variableDeclarator.get().getInitializer().isPresent()) {
            Expression initializer = variableDeclarator.get().getInitializer().get();
            if (initializer.isObjectCreationExpr() && isOldType(initializer.asObjectCreationExpr().getType().getNameAsString())) {
                Expression owner = connectorOwners.get(variableDeclarator.get().getNameAsString());
                if (owner != null) {
                    return Optional.of(owner);
                }
            }
        }

        Optional<AssignExpr> assignment = creation.findAncestor(AssignExpr.class);
        if (assignment.isPresent() && assignment.get().getValue().isObjectCreationExpr()) {
            for (String key : referenceKeys(assignment.get().getTarget())) {
                Expression owner = connectorOwners.get(key);
                if (owner != null) {
                    return Optional.of(owner);
                }
            }
        }

        return Optional.empty();
    }

    private static boolean isOldType(String typeName) {
        return OLD_TYPE.equals(typeName) || typeName.endsWith('.' + OLD_TYPE);
    }

    private static List<String> referenceKeys(Expression expression) {
        List<String> keys = new ArrayList<>();
        keys.add(expression.toString());

        if (expression.isNameExpr()) {
            keys.add(expression.asNameExpr().getNameAsString());
        } else if (expression.isFieldAccessExpr()) {
            FieldAccessExpr fieldAccessExpr = expression.asFieldAccessExpr();
            keys.add(fieldAccessExpr.getNameAsString());
            if (fieldAccessExpr.getScope().isThisExpr()) {
                keys.add("this." + fieldAccessExpr.getNameAsString());
            }
        }

        return keys;
    }
}
