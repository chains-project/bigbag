package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class Main {
    private static final String OLD_SCRIPT_RESULT = "com.gargoylesoftware.htmlunit.ScriptResult";

    public static void main(String[] args) {
        Path sourceRoot = Paths.get(args.length > 0 ? args[0] : ".");

        try {
            Files.walk(sourceRoot)
                    .filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                    .forEach(Main::transformFile);
        } catch (IOException exception) {
            throw new RuntimeException("Failed to transform sources under " + sourceRoot, exception);
        }
    }

    private static void transformFile(Path path) {
        try {
            CompilationUnit compilationUnit = StaticJavaParser.parse(path);
            LexicalPreservingPrinter.setup(compilationUnit);

            if (transformCompilationUnit(compilationUnit)) {
                Files.writeString(path, LexicalPreservingPrinter.print(compilationUnit));
            }
        } catch (IOException exception) {
            throw new RuntimeException("Failed to transform " + path, exception);
        }
    }

    private static boolean transformCompilationUnit(CompilationUnit compilationUnit) {
        boolean changed = false;

        List<VariableDeclarator> variables = compilationUnit.findAll(VariableDeclarator.class);
        for (VariableDeclarator variableDeclarator : variables) {
            changed |= normalizeScriptResultVariable(variableDeclarator);
        }

        List<ObjectCreationExpr> objectCreations = compilationUnit.findAll(ObjectCreationExpr.class);
        for (ObjectCreationExpr objectCreationExpr : objectCreations) {
            changed |= unwrapScriptResult(objectCreationExpr);
        }

        List<MethodCallExpr> methodCalls = compilationUnit.findAll(MethodCallExpr.class);
        for (MethodCallExpr methodCallExpr : methodCalls) {
            changed |= unwrapJavaScriptResult(methodCallExpr);
        }

        if (removeOldImport(compilationUnit)) {
            changed = true;
        }
        return changed;
    }

    private static boolean unwrapScriptResult(ObjectCreationExpr objectCreationExpr) {
        if (isOldScriptResultCreation(objectCreationExpr)) {
            objectCreationExpr.replace(objectCreationExpr.getArgument(0).clone());
            return true;
        }
        return false;
    }

    private static boolean normalizeScriptResultVariable(VariableDeclarator variableDeclarator) {
        if (variableDeclarator.getType().asString().equals("ScriptResult")) {
            variableDeclarator.setType("Object");
            if (variableDeclarator.getInitializer().isPresent()
                    && variableDeclarator.getInitializer().get().isObjectCreationExpr()
                    && isOldScriptResultCreation(variableDeclarator.getInitializer().get().asObjectCreationExpr())) {
                variableDeclarator.setInitializer(variableDeclarator.getInitializer().get().asObjectCreationExpr().getArgument(0).clone());
            }
            return true;
        }
        return false;
    }

    private static boolean unwrapJavaScriptResult(MethodCallExpr methodCallExpr) {
        if (methodCallExpr.getArguments().isEmpty()
                && methodCallExpr.getNameAsString().equals("getJavaScriptResult")
                && methodCallExpr.getScope().isPresent()) {
            Expression scope = methodCallExpr.getScope().get();
            if (scope.isObjectCreationExpr() && isOldScriptResultCreation(scope.asObjectCreationExpr())) {
                methodCallExpr.replace(scope.asObjectCreationExpr().getArgument(0).clone());
            } else {
                methodCallExpr.replace(scope.clone());
            }
            return true;
        }
        return false;
    }

    private static boolean isOldScriptResultCreation(ObjectCreationExpr objectCreationExpr) {
        return objectCreationExpr.getType().asString().equals("ScriptResult")
                && objectCreationExpr.getArguments().size() == 1;
    }

    private static boolean removeOldImport(CompilationUnit compilationUnit) {
        return compilationUnit.getImports().removeIf(importDeclaration ->
                !importDeclaration.isAsterisk() && importDeclaration.getNameAsString().equals(OLD_SCRIPT_RESULT));
    }
}
