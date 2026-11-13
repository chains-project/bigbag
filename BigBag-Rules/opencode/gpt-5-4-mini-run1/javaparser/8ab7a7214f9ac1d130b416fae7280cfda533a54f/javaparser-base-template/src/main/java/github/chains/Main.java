package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.type.Type;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.ArrayList;

public class Main {
    private static final String OLD_SCRIPT_RESULT_FQN = "com.gargoylesoftware.htmlunit.ScriptResult";
    private static final String OLD_SCRIPT_RESULT_SIMPLE_NAME = "ScriptResult";
    private static final String OLD_GET_JAVASCRIPT_RESULT = "getJavaScriptResult";

    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: Main <source-root>");
            return;
        }

        Path sourceRoot = Paths.get(args[0]);
        try {
            List<Path> javaFiles = Files.walk(sourceRoot)
                    .filter(path -> path.toString().endsWith(".java"))
                    .collect(Collectors.toList());

            for (Path javaFile : javaFiles) {
                transform(javaFile);
            }
        }
        catch (IOException exception) {
            throw new RuntimeException("Failed to transform sources under " + sourceRoot, exception);
        }
    }

    private static void transform(Path javaFile) throws IOException {
        CompilationUnit compilationUnit = StaticJavaParser.parse(javaFile);

        boolean changed = compilationUnit.getImports().removeIf(Main::isOldScriptResultImport);
        changed |= rewriteScriptResultVariableDeclarations(compilationUnit);
        changed |= rewriteGetJavaScriptResultCalls(compilationUnit);

        if (changed) {
            Files.writeString(javaFile, compilationUnit.toString());
        }
    }

    private static boolean rewriteScriptResultVariableDeclarations(CompilationUnit compilationUnit) {
        boolean changed = false;

        for (VariableDeclarator variableDeclarator : compilationUnit.findAll(VariableDeclarator.class)) {
            if (isOldScriptResultType(variableDeclarator.getType()) && variableDeclarator.getInitializer().isPresent()) {
                Optional<Expression> unwrapped = unwrapScriptResultExpression(variableDeclarator.getInitializer().get());
                if (unwrapped.isPresent()) {
                    variableDeclarator.setType("Object");
                    variableDeclarator.setInitializer(unwrapped.get());
                    changed = true;
                }
            }
        }

        return changed;
    }

    private static boolean rewriteGetJavaScriptResultCalls(CompilationUnit compilationUnit) {
        boolean changed = false;

        List<MethodCallExpr> matches = new ArrayList<>();
        for (MethodCallExpr methodCallExpr : compilationUnit.findAll(MethodCallExpr.class)) {
            if (isOldGetJavaScriptResultCall(methodCallExpr) && methodCallExpr.getScope().isPresent()) {
                matches.add(methodCallExpr);
            }
        }

        for (MethodCallExpr methodCallExpr : matches) {
            methodCallExpr.replace(methodCallExpr.getScope().get().clone());
            changed = true;
        }

        return changed;
    }

    private static boolean isOldScriptResultImport(ImportDeclaration importDeclaration) {
        return !importDeclaration.isAsterisk() && importDeclaration.getNameAsString().equals(OLD_SCRIPT_RESULT_FQN);
    }

    private static boolean isOldScriptResultType(Type type) {
        return type.isClassOrInterfaceType() && type.asClassOrInterfaceType().getNameAsString().equals(OLD_SCRIPT_RESULT_SIMPLE_NAME);
    }

    private static boolean isOldScriptResultCreation(ObjectCreationExpr objectCreationExpr) {
        return objectCreationExpr.getType().getNameAsString().equals(OLD_SCRIPT_RESULT_SIMPLE_NAME)
                && objectCreationExpr.getArguments().size() == 1;
    }

    private static boolean isOldGetJavaScriptResultCall(MethodCallExpr methodCallExpr) {
        return methodCallExpr.getNameAsString().equals(OLD_GET_JAVASCRIPT_RESULT);
    }

    private static Optional<Expression> unwrapScriptResultExpression(Expression expression) {
        if (expression.isObjectCreationExpr() && isOldScriptResultCreation(expression.asObjectCreationExpr())) {
            return Optional.of(expression.asObjectCreationExpr().getArgument(0));
        }

        if (expression.isMethodCallExpr() && isOldGetJavaScriptResultCall(expression.asMethodCallExpr())) {
            return expression.asMethodCallExpr().getScope().flatMap(Main::unwrapScriptResultExpression);
        }

        return Optional.empty();
    }
}
