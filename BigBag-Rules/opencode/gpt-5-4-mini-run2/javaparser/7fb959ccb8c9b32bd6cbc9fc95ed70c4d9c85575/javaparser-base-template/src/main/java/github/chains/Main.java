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
import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.ThisExpr;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class Main {
    private static final String OLD_SERVLET_PREFIX = "javax.servlet";
    private static final String NEW_SERVLET_PREFIX = "jakarta.servlet";
    private static final String OLD_CONNECTOR_FQCN = "org.eclipse.jetty.server.nio.SelectChannelConnector";
    private static final String NEW_CONNECTOR_FQCN = "org.eclipse.jetty.server.ServerConnector";
    private static final String SERVER_TYPE = "org.eclipse.jetty.server.Server";

    public static void main(String[] args) throws IOException {
        Path inputRoot = args.length > 0 ? Paths.get(args[0]) : Paths.get(".");
        Path outputRoot = args.length > 1 ? Paths.get(args[1]) : inputRoot;

        Files.walk(inputRoot)
                .filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                .forEach(path -> {
                    try {
                        transformFile(inputRoot, outputRoot, path);
                    } catch (IOException e) {
                        throw new RuntimeException("Failed to transform " + path, e);
                    }
                });
    }

    private static void transformFile(Path inputRoot, Path outputRoot, Path sourceFile) throws IOException {
        CompilationUnit cu = StaticJavaParser.parse(sourceFile);
        LexicalPreservingPrinter.setup(cu);

        boolean changed = false;
        changed |= rewriteServletImports(cu);
        changed |= rewriteConnectorInstantiations(cu);

        if (!changed) {
            return;
        }

        Path relative = inputRoot.relativize(sourceFile);
        Path destination = outputRoot.resolve(relative);
        Path parent = destination.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.write(destination, LexicalPreservingPrinter.print(cu).getBytes(StandardCharsets.UTF_8));
    }

    private static boolean rewriteServletImports(CompilationUnit cu) {
        boolean changed = false;
        for (ImportDeclaration importDeclaration : cu.getImports()) {
            String imported = importDeclaration.getNameAsString();
            if (imported.startsWith(OLD_SERVLET_PREFIX)) {
                importDeclaration.setName(imported.replace(OLD_SERVLET_PREFIX, NEW_SERVLET_PREFIX));
                changed = true;
            }
        }
        return changed;
    }

    private static boolean rewriteConnectorInstantiations(CompilationUnit cu) {
        boolean changed = false;
        boolean needsConnectorImport = false;
        for (ObjectCreationExpr creationExpr : cu.findAll(ObjectCreationExpr.class)) {
            if (!isTypeNamed(creationExpr, OLD_CONNECTOR_FQCN)) {
                continue;
            }

            Optional<Expression> serverExpression = findServerExpressionForConnector(creationExpr);
            if (!serverExpression.isPresent()) {
                continue;
            }

            creationExpr.setType(NEW_CONNECTOR_FQCN);
            creationExpr.getArguments().clear();
            creationExpr.addArgument(serverExpression.get().clone());
            changed |= rewriteContainingDeclarationType(creationExpr);
            needsConnectorImport = true;
            changed = true;
        }

        if (needsConnectorImport) {
            ensureImport(cu, NEW_CONNECTOR_FQCN);
        }

        return changed;
    }

    private static boolean rewriteContainingDeclarationType(ObjectCreationExpr creationExpr) {
        Optional<VariableDeclarator> variableDeclarator = creationExpr.findAncestor(VariableDeclarator.class)
                .filter(var -> var.getInitializer().orElse(null) == creationExpr);
        if (variableDeclarator.isPresent()) {
            variableDeclarator.get().setType(NEW_CONNECTOR_FQCN);
            return true;
        }

        Optional<AssignExpr> assignExpr = creationExpr.findAncestor(AssignExpr.class)
                .filter(assign -> assign.getValue() == creationExpr);
        if (assignExpr.isPresent()) {
            return rewriteAssignedType(assignExpr.get().getTarget());
        }

        return false;
    }

    private static boolean rewriteAssignedType(Expression target) {
        if (target.isNameExpr()) {
            Optional<VariableDeclarator> variableDeclarator = target.asNameExpr().getNameAsString().isEmpty()
                    ? Optional.empty()
                    : target.findCompilationUnit()
                        .flatMap(cu -> cu.findAll(VariableDeclarator.class).stream()
                                .filter(variable -> variable.getNameAsString().equals(target.asNameExpr().getNameAsString()))
                                .findFirst());
            if (variableDeclarator.isPresent()) {
                variableDeclarator.get().setType(NEW_CONNECTOR_FQCN);
                return true;
            }
        }
        return false;
    }

    private static void ensureImport(CompilationUnit cu, String fqcn) {
        boolean alreadyImported = cu.getImports().stream().anyMatch(i -> i.getNameAsString().equals(fqcn));
        if (!alreadyImported) {
            cu.addImport(fqcn);
        }
    }

    private static boolean isTypeNamed(ObjectCreationExpr creationExpr, String simpleName) {
        return creationExpr.getType().asString().equals(simpleName)
                || creationExpr.getType().asString().endsWith('.' + simpleName.substring(simpleName.lastIndexOf('.') + 1));
    }

    private static Optional<Expression> findServerExpressionForConnector(ObjectCreationExpr creationExpr) {
        Optional<Expression> directReceiver = findDirectAddConnectorReceiver(creationExpr);
        if (directReceiver.isPresent()) {
            return directReceiver;
        }

        Optional<Expression> assignedTarget = findAssignedTarget(creationExpr);
        if (assignedTarget.isPresent()) {
            Optional<Expression> receiver = findAddConnectorReceiverForTarget(creationExpr, assignedTarget.get());
            if (receiver.isPresent()) {
                return receiver;
            }
        }

        return findUniqueServerTypedExpression(creationExpr);
    }

    private static Optional<Expression> findDirectAddConnectorReceiver(ObjectCreationExpr creationExpr) {
        return creationExpr.findAncestor(MethodCallExpr.class)
                .filter(call -> "addConnector".equals(call.getNameAsString()))
                .filter(call -> call.getArguments().stream().anyMatch(arg -> arg == creationExpr))
                .flatMap(MethodCallExpr::getScope);
    }

    private static Optional<Expression> findAssignedTarget(ObjectCreationExpr creationExpr) {
        Optional<VariableDeclarator> variableDeclarator = creationExpr.findAncestor(VariableDeclarator.class)
                .filter(var -> var.getInitializer().orElse(null) == creationExpr);
        if (variableDeclarator.isPresent()) {
            return Optional.of(new NameExpr(variableDeclarator.get().getNameAsString()));
        }

        Optional<AssignExpr> assignExpr = creationExpr.findAncestor(AssignExpr.class)
                .filter(assign -> assign.getValue() == creationExpr);
        if (assignExpr.isPresent()) {
            return Optional.of(assignExpr.get().getTarget().clone());
        }

        return Optional.empty();
    }

    private static Optional<Expression> findAddConnectorReceiverForTarget(ObjectCreationExpr creationExpr, Expression target) {
        Optional<Node> scope = creationExpr.findAncestor(Node.class);
        if (!scope.isPresent()) {
            return Optional.empty();
        }

        List<MethodCallExpr> calls = scope.get().findAll(MethodCallExpr.class);
        for (MethodCallExpr call : calls) {
            if (!"addConnector".equals(call.getNameAsString()) || call.getArguments().size() != 1) {
                continue;
            }

            if (sameExpression(call.getArgument(0), target)) {
                return call.getScope().map(Expression::clone);
            }
        }

        return Optional.empty();
    }

    private static Optional<Expression> findUniqueServerTypedExpression(ObjectCreationExpr creationExpr) {
        List<Expression> candidates = new ArrayList<>();

        creationExpr.findAncestor(MethodDeclaration.class).ifPresent(method -> {
            for (Parameter parameter : method.getParameters()) {
                if (isServerType(parameter)) {
                    candidates.add(new NameExpr(parameter.getNameAsString()));
                }
            }
            method.findAll(VariableDeclarator.class).stream()
                    .filter(variable -> isServerType(variable) && variable.getParentNode().isPresent())
                    .filter(variable -> variable.getParentNode().get().findAncestor(MethodDeclaration.class).orElse(null) == method)
                    .map(VariableDeclarator::getNameAsString)
                    .forEach(name -> candidates.add(new NameExpr(name)));
        });

        creationExpr.findAncestor(ConstructorDeclaration.class).ifPresent(constructor -> {
            for (Parameter parameter : constructor.getParameters()) {
                if (isServerType(parameter)) {
                    candidates.add(new NameExpr(parameter.getNameAsString()));
                }
            }
            constructor.findAll(VariableDeclarator.class).stream()
                    .filter(variable -> isServerType(variable) && variable.getParentNode().isPresent())
                    .filter(variable -> variable.getParentNode().get().findAncestor(ConstructorDeclaration.class).orElse(null) == constructor)
                    .map(VariableDeclarator::getNameAsString)
                    .forEach(name -> candidates.add(new NameExpr(name)));
        });

        creationExpr.findAncestor(ClassOrInterfaceDeclaration.class).ifPresent(clazz -> {
            for (FieldDeclaration field : clazz.getFields()) {
                for (VariableDeclarator variable : field.getVariables()) {
                    if (isServerType(variable)) {
                        candidates.add(field.isStatic() ? new NameExpr(variable.getNameAsString()) : new FieldAccessExpr(new ThisExpr(), variable.getNameAsString()));
                    }
                }
            }
        });

        if (candidates.size() == 1) {
            return Optional.of(candidates.get(0));
        }

        return Optional.empty();
    }

    private static boolean isServerType(Parameter parameter) {
        return isServerType(parameter.getType().asString());
    }

    private static boolean isServerType(VariableDeclarator variableDeclarator) {
        return isServerType(variableDeclarator.getType().asString());
    }

    private static boolean isServerType(String typeName) {
        return SERVER_TYPE.equals(typeName) || typeName.endsWith(".Server");
    }

    private static boolean sameExpression(Expression left, Expression right) {
        return canonicalName(left).equals(canonicalName(right));
    }

    private static String canonicalName(Expression expression) {
        if (expression.isNameExpr()) {
            return expression.asNameExpr().getNameAsString();
        }
        if (expression.isFieldAccessExpr()) {
            FieldAccessExpr fieldAccessExpr = expression.asFieldAccessExpr();
            String scope = canonicalName(fieldAccessExpr.getScope());
            return scope.isEmpty() ? fieldAccessExpr.getNameAsString() : scope + "." + fieldAccessExpr.getNameAsString();
        }
        return expression.toString().replace("this.", "");
    }
}
