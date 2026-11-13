package github.chains;

import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.CastExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;
import com.github.javaparser.StaticJavaParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

public class Main {
    private static final Set<String> TARGET_METHODS = Set.of("setFuture", "setCompatibleFuture");

    public static void main(String[] args) {
        Path sourceRoot = args.length > 0 ? Paths.get(args[0]) : Paths.get(".");
        if (!Files.exists(sourceRoot)) {
            throw new IllegalArgumentException("Source root does not exist: " + sourceRoot);
        }

        ParserConfiguration configuration = new ParserConfiguration();
        StaticJavaParser.setConfiguration(configuration);

        int changedFiles = 0;
        List<Path> javaFiles = collectJavaFiles(sourceRoot);
        for (Path javaFile : javaFiles) {
            if (transformFile(javaFile)) {
                changedFiles++;
            }
        }

        System.out.println("Updated " + changedFiles + " file(s) under " + sourceRoot.toAbsolutePath());
    }

    private static List<Path> collectJavaFiles(Path sourceRoot) {
        List<Path> files = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(sourceRoot)) {
            stream.filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                    .filter(path -> !path.toString().contains("/target/"))
                    .forEach(files::add);
        } catch (IOException e) {
            throw new RuntimeException("Failed to scan Java files under " + sourceRoot, e);
        }
        return files;
    }

    private static boolean transformFile(Path file) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(file);
            LexicalPreservingPrinter.setup(cu);

            boolean[] changed = {false};
            cu.findAll(MethodCallExpr.class).forEach(call -> {
                if (transformAsyncRpcResultMethodCall(call)) {
                    changed[0] = true;
                    return;
                }
                if (!TARGET_METHODS.contains(call.getNameAsString())) {
                    return;
                }
                if (call.getArguments().size() != 1) {
                    return;
                }
                if (!isTargetDubboCall(call)) {
                    return;
                }

                Expression argument = call.getArgument(0);
                if (isResponseFutureAccess(argument)) {
                    return;
                }
                if (!isAsyncRpcResultExpression(argument)) {
                    return;
                }

                MethodCallExpr replacement = new MethodCallExpr(argument.clone(), "getResponseFuture");
                call.setArgument(0, replacement);
                changed[0] = true;
            });

            cu.findAll(ObjectCreationExpr.class).forEach(creation -> {
                if (transformAsyncRpcResultConstructor(creation)) {
                    changed[0] = true;
                }
            });

            if (!changed[0]) {
                return false;
            }

            Files.writeString(file, LexicalPreservingPrinter.print(cu), StandardCharsets.UTF_8);
            return true;
        } catch (Exception e) {
            throw new RuntimeException("Failed to transform " + file, e);
        }
    }

    private static boolean transformAsyncRpcResultMethodCall(MethodCallExpr call) {
        if (!"subscribeTo".equals(call.getNameAsString())) {
            return false;
        }
        if (call.getArguments().size() != 1) {
            return false;
        }
        if (!isAsyncRpcResultReceiver(call.getScope())) {
            return false;
        }

        call.setName("setResponseFuture");
        return true;
    }

    private static boolean transformAsyncRpcResultConstructor(ObjectCreationExpr creation) {
        if (!typeNameContainsAsyncRpcResult(creation.getType().asString())) {
            return false;
        }
        if (creation.getArguments().size() != 1) {
            return false;
        }

        creation.replace(StaticJavaParser.parseExpression(
                creation.getType().asString() + ".newDefaultAsyncResult(" + creation.getArgument(0) + ")"));
        return true;
    }

    private static boolean isTargetDubboCall(MethodCallExpr call) {
        if (call.getNameAsString().equals("setFuture")) {
            return matchesContextType(call.getScope(), "RpcContext");
        }
        if (call.getNameAsString().equals("setCompatibleFuture")) {
            return matchesContextType(call.getScope(), "FutureContext");
        }
        return false;
    }

    private static boolean matchesContextType(Optional<Expression> scope, String expectedTypeName) {
        if (scope.isEmpty()) {
            return false;
        }

        Expression expression = stripParens(scope.get());
        if (!(expression instanceof MethodCallExpr)) {
            return false;
        }

        MethodCallExpr methodCallExpr = (MethodCallExpr) expression;
        if (!"getContext".equals(methodCallExpr.getNameAsString())) {
            return false;
        }

        Optional<Expression> nestedScope = methodCallExpr.getScope();
        if (nestedScope.isEmpty()) {
            return false;
        }

        Expression nested = stripParens(nestedScope.get());
        String scopeText = nested.toString();
        return scopeText.endsWith(expectedTypeName) || scopeText.endsWith('.' + expectedTypeName)
                || scopeText.equals(expectedTypeName)
                || scopeText.endsWith("org.apache.dubbo.rpc." + expectedTypeName);
    }

    private static boolean isResponseFutureAccess(Expression expression) {
        Expression stripped = stripParens(expression);
        if (!(stripped instanceof MethodCallExpr)) {
            return false;
        }
        return ((MethodCallExpr) stripped).getNameAsString().equals("getResponseFuture");
    }

    private static boolean isAsyncRpcResultExpression(Expression expression) {
        Expression stripped = stripParens(expression);

        if (containsAsyncRpcResultText(stripped)) {
            return true;
        }

        if (stripped instanceof NameExpr) {
            return isNameBoundToAsyncRpcResult((NameExpr) stripped);
        }

        if (stripped instanceof CastExpr) {
            return typeNameContainsAsyncRpcResult(((CastExpr) stripped).getType().asString());
        }

        if (stripped instanceof VariableDeclarationExpr) {
            return typeNameContainsAsyncRpcResult(((VariableDeclarationExpr) stripped).getElementType().asString());
        }

        if (stripped instanceof ObjectCreationExpr) {
            return typeNameContainsAsyncRpcResult(((ObjectCreationExpr) stripped).getType().asString());
        }

        if (stripped instanceof MethodCallExpr) {
            MethodCallExpr methodCallExpr = (MethodCallExpr) stripped;
            return containsAsyncRpcResultText(methodCallExpr.getScope().orElse(null))
                    || "newDefaultAsyncResult".equals(methodCallExpr.getNameAsString())
                    || methodCallExpr.toString().contains("AsyncRpcResult");
        }

        if (stripped instanceof FieldAccessExpr) {
            return typeNameContainsAsyncRpcResult(stripped.toString());
        }

        return false;
    }

    private static boolean isNameBoundToAsyncRpcResult(NameExpr nameExpr) {
        String name = nameExpr.getNameAsString();
        Node current = nameExpr;

        while (current != null) {
            if (current instanceof MethodCallExpr) {
                MethodCallExpr methodCallExpr = (MethodCallExpr) current;
                for (Parameter parameter : methodCallExpr.findAll(Parameter.class)) {
                    if (parameter.getNameAsString().equals(name)
                            && typeNameContainsAsyncRpcResult(parameter.getType().asString())) {
                        return true;
                    }
                }
            }

            if (current instanceof CompilationUnit) {
                CompilationUnit cu = (CompilationUnit) current;
                for (VariableDeclarator variableDeclarator : cu.findAll(VariableDeclarator.class)) {
                    if (variableDeclarator.getNameAsString().equals(name)
                            && typeNameContainsAsyncRpcResult(variableDeclarator.getType().asString())) {
                        return true;
                    }
                }
                break;
            }

            if (current instanceof VariableDeclarationExpr) {
                VariableDeclarationExpr variableDeclarationExpr = (VariableDeclarationExpr) current;
                for (VariableDeclarator variableDeclarator : variableDeclarationExpr.getVariables()) {
                    if (variableDeclarator.getNameAsString().equals(name)
                            && typeNameContainsAsyncRpcResult(variableDeclarator.getType().asString())) {
                        return true;
                    }
                }
            }

            if (current instanceof Parameter) {
                Parameter parameter = (Parameter) current;
                if (parameter.getNameAsString().equals(name)
                        && typeNameContainsAsyncRpcResult(parameter.getType().asString())) {
                    return true;
                }
            }

            current = current.getParentNode().orElse(null);
        }
        return false;
    }

    private static boolean containsAsyncRpcResultText(Expression expression) {
        return expression != null && typeNameContainsAsyncRpcResult(expression.toString());
    }

    private static boolean isAsyncRpcResultReceiver(Optional<Expression> scope) {
        return scope.isPresent() && isAsyncRpcResultExpression(scope.get());
    }

    private static boolean typeNameContainsAsyncRpcResult(String typeName) {
        if (typeName == null) {
            return false;
        }
        String normalized = typeName.replace(" ", "");
        return normalized.endsWith("AsyncRpcResult")
                || normalized.endsWith("org.apache.dubbo.rpc.AsyncRpcResult")
                || normalized.contains("AsyncRpcResult<")
                || normalized.contains("AsyncRpcResult[")
                || normalized.contains("AsyncRpcResult>")
                || normalized.contains("AsyncRpcResult.");
    }

    private static Expression stripParens(Expression expression) {
        Expression current = expression;
        while (current.isEnclosedExpr()) {
            current = current.asEnclosedExpr().getInner();
        }
        return current;
    }
}
