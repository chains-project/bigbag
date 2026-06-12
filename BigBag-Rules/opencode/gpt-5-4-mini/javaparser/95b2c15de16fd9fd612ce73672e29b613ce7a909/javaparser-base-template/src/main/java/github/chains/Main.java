package github.chains;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.utils.SourceRoot.Callback.Result;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;
import com.github.javaparser.utils.SourceRoot;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

public class Main {
    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            throw new IllegalArgumentException("Expected source root path as the first argument");
        }

        Path sourceRootPath = Paths.get(args[0]);
        SourceRoot sourceRoot = new SourceRoot(sourceRootPath);
        sourceRoot.parseParallelized((localPath, absolutePath, result) -> {
            result.getResult().ifPresent(compilationUnit -> {
                LexicalPreservingPrinter.setup(compilationUnit);
                transformCompilationUnit(compilationUnit);
            });
            return Result.SAVE;
        });

        sourceRoot.setPrinter(LexicalPreservingPrinter::print);
        sourceRoot.saveAll();
    }

    private static void transformCompilationUnit(CompilationUnit compilationUnit) {
        replaceOldConnectorTypes(compilationUnit);
        replaceOldConnectorCreations(compilationUnit);
        rewriteServletImports(compilationUnit);
    }

    private static void replaceOldConnectorTypes(CompilationUnit compilationUnit) {
        for (ClassOrInterfaceType type : compilationUnit.findAll(ClassOrInterfaceType.class)) {
            if (isOldSelectChannelConnectorType(type)) {
                type.setName("ServerConnector");
            }
        }
    }

    private static void replaceOldConnectorCreations(CompilationUnit compilationUnit) {
        boolean changed = false;
        for (ObjectCreationExpr creation : compilationUnit.findAll(ObjectCreationExpr.class)) {
            if (!isOldSelectChannelConnectorType(creation.getType())) {
                continue;
            }

            Optional<Expression> serverScope = findServerExpressionForConnector(creation);
            if (!serverScope.isPresent()) {
                continue;
            }

            creation.replace(StaticJavaParser.parseExpression(
                    "new ServerConnector(" + serverScope.get().clone() + ", new HttpConnectionFactory())"));
            changed = true;
        }

        ensureJettyConnectorImports(compilationUnit, changed);
    }

    private static Optional<Expression> findServerExpressionForConnector(ObjectCreationExpr creation) {
        Optional<Node> scope = creation.findAncestor(ConstructorDeclaration.class).map(Node.class::cast);
        if (!scope.isPresent()) {
            scope = creation.findAncestor(MethodDeclaration.class).map(Node.class::cast);
        }
        if (!scope.isPresent()) {
            return Optional.empty();
        }

        for (VariableDeclarator variableDeclarator : scope.get().findAll(VariableDeclarator.class)) {
            if (!"Server".equals(variableDeclarator.getType().toString())) {
                continue;
            }

            if (variableDeclarator.getInitializer().isPresent() && isServerInstantiation(variableDeclarator.getInitializer().get())) {
                return Optional.of(new NameExpr(variableDeclarator.getNameAsString()));
            }
        }

        for (AssignExpr assignExpr : scope.get().findAll(AssignExpr.class)) {
            if (!isServerInstantiation(assignExpr.getValue())) {
                continue;
            }

            return Optional.of(assignExpr.getTarget().clone());
        }

        return Optional.empty();
    }

    private static boolean isServerInstantiation(Expression expression) {
        if (!(expression instanceof ObjectCreationExpr)) {
            return false;
        }

        ObjectCreationExpr creationExpr = (ObjectCreationExpr) expression;
        return "Server".equals(creationExpr.getType().toString());
    }

    private static void ensureJettyConnectorImports(CompilationUnit compilationUnit, boolean addConnectorImports) {
        if (addConnectorImports) {
            compilationUnit.addImport("org.eclipse.jetty.server.HttpConnectionFactory");
            compilationUnit.addImport("org.eclipse.jetty.server.ServerConnector");
        }

        if (addConnectorImports) {
            compilationUnit.getImports().removeIf(importDeclaration ->
                    importDeclaration.getNameAsString().equals("org.eclipse.jetty.server.nio.SelectChannelConnector"));
        }
    }

    private static void rewriteServletImports(CompilationUnit compilationUnit) {
        for (var importDeclaration : compilationUnit.getImports()) {
            String importName = importDeclaration.getNameAsString();
            if (importName.startsWith("javax.servlet")) {
                importDeclaration.setName(importName.replaceFirst("^javax\\.servlet", "jakarta.servlet"));
            }
        }

        if (compilationUnit.toString().contains("HttpServletRequest")) {
            compilationUnit.addImport("jakarta.servlet.http.HttpServletRequest");
        }
        if (compilationUnit.toString().contains("HttpServletResponse")) {
            compilationUnit.addImport("jakarta.servlet.http.HttpServletResponse");
        }
        if (compilationUnit.toString().contains("ServletException")) {
            compilationUnit.addImport("jakarta.servlet.ServletException");
        }
    }

    private static boolean isOldSelectChannelConnectorType(ClassOrInterfaceType type) {
        String name = type.getNameAsString();
        if ("SelectChannelConnector".equals(name) || "ServerConnector".equals(name)) {
            return true;
        }
        return "org.eclipse.jetty.server.nio.SelectChannelConnector".equals(type.toString());
    }
}
