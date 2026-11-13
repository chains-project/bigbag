package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.stmt.ExplicitConstructorInvocationStmt;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

public class Main {
    private static final String SSL_FILTER = "org.apache.mina.filter.ssl.SslFilter";
    private static final String SSL_CONTEXT = "javax.net.ssl.SSLContext";
    private static final String PEER_ADDRESS = "PEER_ADDRESS";

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException("Expected a single source directory argument");
        }

        Path root = Paths.get(args[0]);
        ParserConfiguration configuration = new ParserConfiguration();
        JavaParser parser = new JavaParser(configuration);

        Files.walk(root)
                .filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                .forEach(path -> {
                    try {
                        rewrite(parser, path);
                    } catch (IOException e) {
                        throw new RuntimeException("Failed to rewrite " + path, e);
                    }
                });
    }

    private static void rewrite(JavaParser parser, Path path) throws IOException {
        Optional<CompilationUnit> parsed = parser.parse(path).getResult();
        if (parsed.isEmpty()) {
            return;
        }

        CompilationUnit cu = parsed.get();
        LexicalPreservingPrinter.setup(cu);

        boolean changed = false;
        changed |= removeOldSslFilterConstructors(cu);
        changed |= removeOldSslFilterCalls(cu);
        changed |= replaceRemovedSslSessionLookup(cu);
        changed |= removePeerAddressAttributeWrites(cu);
        changed |= replaceSslFilterThrowsDeclarations(cu);

        if (changed) {
            Files.writeString(path, LexicalPreservingPrinter.print(cu), StandardCharsets.UTF_8);
        }
    }

    private static boolean removeOldSslFilterConstructors(CompilationUnit cu) {
        boolean changed = false;

        for (ObjectCreationExpr expr : cu.findAll(ObjectCreationExpr.class)) {
            if (isSslFilterType(expr.getType()) && expr.getArguments().size() == 2 && isSslContext(expr.getArguments().get(0))) {
                expr.getArguments().remove(1);
                changed = true;
            }
        }

        for (ExplicitConstructorInvocationStmt stmt : cu.findAll(ExplicitConstructorInvocationStmt.class)) {
            if (!stmt.isThis() && stmt.getArguments().size() == 2 && isSslContext(stmt.getArguments().get(0)) && isEnclosingClassExtendsSslFilter(stmt)) {
                stmt.getArguments().remove(1);
                changed = true;
            }
        }

        return changed;
    }

    private static boolean removeOldSslFilterCalls(CompilationUnit cu) {
        boolean changed = false;

        for (ExpressionStmt stmt : cu.findAll(ExpressionStmt.class)) {
            Node expression = stmt.getExpression();
            if (!(expression instanceof MethodCallExpr)) {
                continue;
            }

            MethodCallExpr call = (MethodCallExpr) expression;
            if ("setUseClientMode".equals(call.getNameAsString()) || "initiateHandshake".equals(call.getNameAsString())) {
                stmt.remove();
                changed = true;
            }
        }

        return changed;
    }

    private static boolean removePeerAddressAttributeWrites(CompilationUnit cu) {
        boolean changed = false;

        for (ExpressionStmt stmt : cu.findAll(ExpressionStmt.class)) {
            Node expression = stmt.getExpression();
            if (!(expression instanceof MethodCallExpr)) {
                continue;
            }

            MethodCallExpr call = (MethodCallExpr) expression;
            if (!"setAttribute".equals(call.getNameAsString()) || call.getArguments().isEmpty()) {
                continue;
            }

            Expression firstArg = call.getArgument(0);
            if (isPeerAddressReference(firstArg)) {
                stmt.remove();
                changed = true;
            }
        }

        return changed;
    }

    private static boolean replaceRemovedSslSessionLookup(CompilationUnit cu) {
        boolean changed = false;

        for (MethodCallExpr call : cu.findAll(MethodCallExpr.class)) {
            if (!"getSslSession".equals(call.getNameAsString()) || call.getArguments().size() != 1) {
                continue;
            }

            String sessionExpr = call.getArgument(0).toString();
            Expression replacement = StaticJavaParser.parseExpression(
                    "(javax.net.ssl.SSLSession) " + sessionExpr + ".getAttribute(org.apache.mina.filter.ssl.SslFilter.SSL_SECURED)");
            call.replace(replacement);
            changed = true;
        }

        return changed;
    }

    private static boolean replaceSslFilterThrowsDeclarations(CompilationUnit cu) {
        boolean changed = false;

        for (com.github.javaparser.ast.body.MethodDeclaration method : cu.findAll(com.github.javaparser.ast.body.MethodDeclaration.class)) {
            if (!isEnclosingClassExtendsSslFilter(method)) {
                continue;
            }
            if (method.getThrownExceptions().stream().anyMatch(t -> "Exception".equals(t.toString()))) {
                continue;
            }
            if (method.getBody().isEmpty()) {
                continue;
            }
            boolean callsSuper = method.getBody().get().findAll(MethodCallExpr.class).stream()
                    .anyMatch(call -> call.getScope().filter(Expression::isSuperExpr).isPresent());
            if (callsSuper) {
                method.addThrownException(new ClassOrInterfaceType(null, "Exception"));
                changed = true;
            }
        }

        return changed;
    }

    private static boolean isSslFilterType(ClassOrInterfaceType type) {
        String name = type.getNameAsString();
        if (!"SslFilter".equals(name)) {
            return false;
        }
        return true;
    }

    private static boolean isSslContext(Expression expression) {
        return expression.toString().contains("sslContext") || expression.toString().contains("SSLContext");
    }

    private static boolean isPeerAddressReference(Expression expression) {
        if (expression instanceof NameExpr) {
            return PEER_ADDRESS.equals(((NameExpr) expression).getNameAsString());
        }
        if (expression instanceof FieldAccessExpr) {
            return PEER_ADDRESS.equals(((FieldAccessExpr) expression).getNameAsString());
        }
        return false;
    }

    private static boolean isEnclosingClassExtendsSslFilter(ExplicitConstructorInvocationStmt stmt) {
        return stmt.findAncestor(ClassOrInterfaceDeclaration.class)
                .map(clazz -> clazz.getExtendedTypes().stream().anyMatch(Main::isSslFilterType))
                .orElse(false);
    }

    private static boolean isEnclosingClassExtendsSslFilter(com.github.javaparser.ast.body.MethodDeclaration method) {
        return method.findAncestor(ClassOrInterfaceDeclaration.class)
                .map(clazz -> clazz.getExtendedTypes().stream().anyMatch(Main::isSslFilterType))
                .orElse(false);
    }

}
