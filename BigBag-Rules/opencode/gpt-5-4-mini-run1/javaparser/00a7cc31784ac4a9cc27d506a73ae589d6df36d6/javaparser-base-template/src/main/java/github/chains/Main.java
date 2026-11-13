package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.ExplicitConstructorInvocationStmt;
import com.github.javaparser.ast.type.ClassOrInterfaceType;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.stream.Stream;

public class Main {
    public static void main(String[] args) {
        if (args.length == 0) {
            System.err.println("Usage: Main <source-root>");
            System.exit(1);
        }

        Path sourceRoot = Paths.get(args[0]).toAbsolutePath().normalize();
        try (Stream<Path> paths = Files.walk(sourceRoot)) {
            paths.filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                    .forEach(Main::rewriteFile);
        } catch (IOException e) {
            throw new RuntimeException("Failed to process source root: " + sourceRoot, e);
        }
    }

    private static void rewriteFile(Path javaFile) {
        try {
            CompilationUnit compilationUnit = StaticJavaParser.parse(javaFile);
            boolean changed = false;

            changed |= rewriteSslFilterConstructorCalls(compilationUnit);
            changed |= rewriteExplicitSslFilterSuperCalls(compilationUnit);
            changed |= removeRemovedSslFilterMethodCalls(compilationUnit, "setUseClientMode");
            changed |= removeRemovedSslFilterMethodCalls(compilationUnit, "initiateHandshake");
            changed |= rewriteGetSslSessionCalls(compilationUnit);
            changed |= removePeerAddressAttributeWrite(compilationUnit);
            changed |= widenOnPreAddThrowsClause(compilationUnit);

            if (changed) {
                Files.writeString(javaFile, compilationUnit.toString());
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to rewrite " + javaFile, e);
        }
    }

    private static boolean rewriteSslFilterConstructorCalls(CompilationUnit compilationUnit) {
        boolean changed = false;
        for (ObjectCreationExpr creationExpr : compilationUnit.findAll(ObjectCreationExpr.class)) {
            if (isSslFilterTypeName(creationExpr.getType().getNameAsString())
                    && creationExpr.getArguments().size() == 2) {
                creationExpr.getArguments().remove(1);
                changed = true;
            }
        }
        return changed;
    }

    private static boolean rewriteExplicitSslFilterSuperCalls(CompilationUnit compilationUnit) {
        boolean changed = false;
        for (ExplicitConstructorInvocationStmt invocationStmt : compilationUnit.findAll(ExplicitConstructorInvocationStmt.class)) {
            if (!invocationStmt.toString().startsWith("super(")) {
                continue;
            }
            if (invocationStmt.getArguments().size() != 2) {
                continue;
            }

            Optional<ClassOrInterfaceDeclaration> owner = invocationStmt.findAncestor(ClassOrInterfaceDeclaration.class);
            if (owner.isPresent() && extendsSslFilter(owner.get())) {
                invocationStmt.getArguments().remove(1);
                changed = true;
            }
        }
        return changed;
    }

    private static boolean removeRemovedSslFilterMethodCalls(CompilationUnit compilationUnit, String methodName) {
        boolean changed = false;
        for (MethodCallExpr callExpr : compilationUnit.findAll(MethodCallExpr.class)) {
            if (!callExpr.getNameAsString().equals(methodName)) {
                continue;
            }
            if (callExpr.getArguments().size() != 1) {
                continue;
            }
            if (!isSslFilterCallSite(callExpr)) {
                continue;
            }

            Optional<ExpressionStmt> expressionStmt = callExpr.findAncestor(ExpressionStmt.class);
            if (expressionStmt.isPresent()) {
                expressionStmt.get().remove();
                changed = true;
            }
        }
        return changed;
    }

    private static boolean removePeerAddressAttributeWrite(CompilationUnit compilationUnit) {
        boolean changed = false;
        for (MethodCallExpr callExpr : compilationUnit.findAll(MethodCallExpr.class)) {
            if (!callExpr.getNameAsString().equals("setAttribute") || callExpr.getArguments().size() != 2) {
                continue;
            }
            Expression firstArgument = callExpr.getArgument(0);
            if (!(firstArgument.isNameExpr() && firstArgument.asNameExpr().getNameAsString().equals("PEER_ADDRESS"))) {
                continue;
            }

            Optional<ExpressionStmt> expressionStmt = callExpr.findAncestor(ExpressionStmt.class);
            if (expressionStmt.isPresent()) {
                expressionStmt.get().remove();
                changed = true;
            }
        }
        return changed;
    }

    private static boolean rewriteGetSslSessionCalls(CompilationUnit compilationUnit) {
        boolean changed = false;
        for (MethodCallExpr callExpr : compilationUnit.findAll(MethodCallExpr.class)) {
            if (!callExpr.getNameAsString().equals("getSslSession") || callExpr.getArguments().size() != 1) {
                continue;
            }
            if (!isSslFilterCallSite(callExpr)) {
                continue;
            }

            String sessionExpr = callExpr.getArgument(0).toString();
            callExpr.replace(StaticJavaParser.parseExpression(
                    "((javax.net.ssl.SSLSession) " + sessionExpr
                            + ".getAttribute(org.apache.mina.filter.ssl.SslFilter.SSL_SECURED))"));
            changed = true;
        }
        return changed;
    }

    private static boolean widenOnPreAddThrowsClause(CompilationUnit compilationUnit) {
        boolean changed = false;
        for (com.github.javaparser.ast.body.MethodDeclaration methodDeclaration : compilationUnit.findAll(com.github.javaparser.ast.body.MethodDeclaration.class)) {
            if (!methodDeclaration.getNameAsString().equals("onPreAdd")) {
                continue;
            }
            if (methodDeclaration.getThrownExceptions().stream().anyMatch(type -> type.asString().equals("Exception"))) {
                continue;
            }
            if (!methodDeclaration.getBody().isPresent()) {
                continue;
            }

            boolean callsSuperOnPreAdd = methodDeclaration.getBody().get().findAll(MethodCallExpr.class).stream()
                    .anyMatch(call -> call.getScope().map(Expression::toString).orElse("").equals("super")
                            && call.getNameAsString().equals("onPreAdd"));
            if (!callsSuperOnPreAdd) {
                continue;
            }

            methodDeclaration.addThrownException(StaticJavaParser.parseClassOrInterfaceType("Exception"));
            changed = true;
        }
        return changed;
    }

    private static boolean isSslFilterCallSite(MethodCallExpr callExpr) {
        Optional<Expression> scope = callExpr.getScope();
        if (scope.isPresent()) {
            String scopeText = scope.get().toString();
            return scopeText.equals("sslFilter") || scopeText.endsWith(".sslFilter") || scopeText.contains("sslFilter");
        }

        Optional<ClassOrInterfaceDeclaration> owner = callExpr.findAncestor(ClassOrInterfaceDeclaration.class);
        return owner.isPresent() && extendsSslFilter(owner.get());
    }

    private static boolean extendsSslFilter(ClassOrInterfaceDeclaration declaration) {
        return declaration.getExtendedTypes().stream()
                .anyMatch(type -> isSslFilterTypeName(type.getNameAsString()));
    }

    private static boolean isSslFilterTypeName(String simpleTypeName) {
        return simpleTypeName.equals("SslFilter") || simpleTypeName.equals("SSLFilter");
    }
}
