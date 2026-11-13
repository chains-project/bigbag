package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.ExplicitConstructorInvocationStmt;
import com.github.javaparser.ast.type.ReferenceType;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class Main {
    private static final String SSL_FILTER = "SslFilter";

    public static void main(String[] args) {
        Path root = Paths.get(args.length > 0 ? args[0] : ".");
        JavaParser parser = new JavaParser(new ParserConfiguration());

        try {
            Files.walk(root)
                .filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                .forEach(path -> processFile(parser, path));
        } catch (IOException e) {
            throw new RuntimeException("Failed to traverse source tree: " + root, e);
        }
    }

    private static void processFile(JavaParser parser, Path path) {
        try {
            ParseResult<CompilationUnit> result = parser.parse(path);
            if (!result.isSuccessful() || result.getResult().isEmpty()) {
                return;
            }

            CompilationUnit cu = result.getResult().get();
            boolean changed = transformCompilationUnit(cu);
            if (changed) {
                Files.writeString(path, cu.toString(), StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to process file: " + path, e);
        }
    }

    private static boolean transformCompilationUnit(CompilationUnit cu) {
        boolean[] changed = new boolean[] { false };

        cu.findAll(ClassOrInterfaceDeclaration.class).forEach(clazz -> {
            if (extendsSslFilter(clazz)) {
                for (MethodDeclaration method : clazz.getMethodsByName("onPreAdd")) {
                    changed[0] |= widenOnPreAddThrowsClause(method);
                }
                for (ConstructorDeclaration constructor : clazz.getConstructors()) {
                    changed[0] |= rewriteSslFilterConstructor(constructor);
                }
            }
        });

        cu.findAll(ExpressionStmt.class).forEach(stmt -> {
            if (rewriteRemovedSslFilterCall(stmt)) {
                changed[0] = true;
            }
        });

        cu.findAll(MethodCallExpr.class).forEach(call -> {
            if ("getSslSession".equals(call.getNameAsString()) && call.getArguments().size() == 1) {
                call.replace(StaticJavaParser.parseExpression("(javax.net.ssl.SSLSession) "
                        + call.getArgument(0) + ".getAttribute(org.apache.mina.filter.ssl.SslFilter.SSL_SECURED)"));
                changed[0] = true;
            }
        });

        return changed[0];
    }

    private static boolean extendsSslFilter(ClassOrInterfaceDeclaration clazz) {
        return clazz.getExtendedTypes().stream()
            .map(type -> type.getNameAsString())
            .anyMatch(SSL_FILTER::equals);
    }

    private static boolean rewriteSslFilterConstructor(ConstructorDeclaration constructor) {
        boolean changed = false;
        for (Node node : constructor.getBody().getStatements()) {
            if (node instanceof ExplicitConstructorInvocationStmt) {
                ExplicitConstructorInvocationStmt invocation = (ExplicitConstructorInvocationStmt) node;
                if (!invocation.isThis() && invocation.getArguments().size() == 2) {
                    NodeList<Expression> arguments = new NodeList<>();
                    arguments.add(invocation.getArguments().get(0));
                    invocation.setArguments(arguments);
                    changed = true;
                }
            }
        }
        return changed;
    }

    private static boolean widenOnPreAddThrowsClause(MethodDeclaration method) {
        NodeList<ReferenceType> thrown = new NodeList<>();
        thrown.add(StaticJavaParser.parseType("Exception").asReferenceType());
        if (method.getThrownExceptions().size() == 1
                && method.getThrownExceptions().get(0).asString().equals("SSLException")) {
            method.setThrownExceptions(thrown);
            return true;
        }
        return false;
    }

    private static boolean rewriteRemovedSslFilterCall(ExpressionStmt stmt) {
        Expression expression = stmt.getExpression();
        if (!(expression instanceof MethodCallExpr)) {
            return false;
        }

        MethodCallExpr call = (MethodCallExpr) expression;
        String name = call.getNameAsString();
        if ("setUseClientMode".equals(name) || "initiateHandshake".equals(name)) {
            stmt.remove();
            return true;
        }

        if ("setAttribute".equals(name) && call.getArguments().size() >= 1) {
            Expression firstArg = call.getArgument(0);
            if (isPeerAddressReference(firstArg)) {
                stmt.remove();
                return true;
            }
        }

        return false;
    }

    private static boolean isPeerAddressReference(Expression expression) {
        if (expression instanceof NameExpr) {
            return "PEER_ADDRESS".equals(((NameExpr) expression).getNameAsString());
        }
        if (expression instanceof FieldAccessExpr) {
            return "PEER_ADDRESS".equals(((FieldAccessExpr) expression).getNameAsString());
        }
        return false;
    }
}
