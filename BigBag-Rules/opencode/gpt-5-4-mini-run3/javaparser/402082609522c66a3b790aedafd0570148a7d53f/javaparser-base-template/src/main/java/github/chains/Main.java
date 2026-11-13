package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class Main {
    public static void main(String[] args) {
        if (args.length != 1) {
            throw new IllegalArgumentException("Expected a single source directory path");
        }

        Path root = Paths.get(args[0]);
        try {
            Files.walk(root)
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .forEach(Main::transformFile);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void transformFile(Path file) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(file);
            LexicalPreservingPrinter.setup(cu);

            boolean changed = false;
            List<MethodCallExpr> candidates = cu.findAll(MethodCallExpr.class);
            for (MethodCallExpr submitCall : candidates) {
                if (!isOldAsyncSubmit(submitCall)) {
                    continue;
                }

                Expression replacement = rewriteOldAsyncSubmit(submitCall);
                submitCall.replace(replacement);
                changed = true;
            }

            if (changed) {
                ensureImport(cu, "org.spongepowered.api.Sponge");
                Files.writeString(file, LexicalPreservingPrinter.print(cu), StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to transform " + file, e);
        }
    }

    private static boolean isOldAsyncSubmit(MethodCallExpr call) {
        if (!"submit".equals(call.getNameAsString()) || call.getArguments().size() != 1 || call.getScope().isEmpty()) {
            return false;
        }

        return containsAsyncBuilder(call.getScope().get());
    }

    private static boolean containsAsyncBuilder(Expression expression) {
        Expression current = expression;
        boolean sawAsync = false;

        while (current instanceof MethodCallExpr) {
            MethodCallExpr methodCall = current.asMethodCallExpr();
            if (isAsyncCall(methodCall)) {
                sawAsync = true;
            }

            if (isTaskBuilderCall(methodCall)) {
                return sawAsync;
            }

            current = methodCall.getScope().orElse(null);
            if (current == null) {
                break;
            }
        }

        return false;
    }

    private static boolean isTaskBuilderCall(MethodCallExpr call) {
        return "builder".equals(call.getNameAsString())
                && call.getArguments().isEmpty()
                && call.getScope().isPresent()
                && call.getScope().get().isNameExpr()
                && "Task".equals(call.getScope().get().asNameExpr().getNameAsString());
    }

    private static boolean isAsyncCall(MethodCallExpr call) {
        return "async".equals(call.getNameAsString()) && call.getArguments().isEmpty();
    }

    private static Expression rewriteOldAsyncSubmit(MethodCallExpr submitCall) {
        Expression builderChain = removeAsyncCalls(submitCall.getScope().orElseThrow());

        MethodCallExpr pluginCall = new MethodCallExpr(builderChain, "plugin",
                new NodeList<>(submitCall.getArgument(0).clone()));
        MethodCallExpr buildCall = new MethodCallExpr(pluginCall, "build");
        MethodCallExpr asyncSchedulerCall = new MethodCallExpr(new NameExpr("Sponge"), "asyncScheduler");
        return new MethodCallExpr(asyncSchedulerCall, "submit", new NodeList<>(buildCall));
    }

    private static Expression removeAsyncCalls(Expression expression) {
        if (!(expression instanceof MethodCallExpr)) {
            return expression.clone();
        }

        MethodCallExpr call = expression.asMethodCallExpr();
        if (isAsyncCall(call)) {
            return removeAsyncCalls(call.getScope().orElseThrow());
        }

        MethodCallExpr clone = call.clone();
        clone.getScope().ifPresent(scope -> clone.setScope(removeAsyncCalls(scope)));
        return clone;
    }

    private static void ensureImport(CompilationUnit cu, String importName) {
        if (cu.getImports().stream().noneMatch(imp -> !imp.isAsterisk() && imp.getNameAsString().equals(importName))) {
            cu.addImport(importName);
        }
    }
}
