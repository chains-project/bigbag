package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.body.VariableDeclarator;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

public class Main {
    private static final String OLD_SCRIPT_RESULT = "com.gargoylesoftware.htmlunit.ScriptResult";
    private static final String OLD_METHOD = "getJavaScriptResult";

    public static void main(final String[] args) throws IOException {
        if (args.length != 1) {
            throw new IllegalArgumentException("Usage: Main <source-directory>");
        }

        Path sourceRoot = Paths.get(args[0]);
        if (!Files.isDirectory(sourceRoot)) {
            throw new IllegalArgumentException("Source directory does not exist: " + sourceRoot);
        }

        StaticJavaParser.getConfiguration().setAttributeComments(false);

        try (Stream<Path> paths = Files.walk(sourceRoot)) {
            paths.filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                    .forEach(Main::transformFile);
        }
    }

    private static void transformFile(final Path sourceFile) {
        try {
            CompilationUnit compilationUnit = StaticJavaParser.parse(sourceFile);

            boolean changed = new ScriptResultTransformer().transform(compilationUnit);
            if (changed) {
                compilationUnit.getImports().removeIf(importDeclaration ->
                        importDeclaration.getNameAsString().equals(OLD_SCRIPT_RESULT));
                Files.write(sourceFile, compilationUnit.toString().getBytes(StandardCharsets.UTF_8));
            }
        }
        catch (IOException exception) {
            throw new IllegalStateException("Failed to update " + sourceFile, exception);
        }
    }

    private static final class ScriptResultTransformer {
        boolean transform(final CompilationUnit compilationUnit) {
            boolean changed = false;
            for (VariableDeclarator variableDeclarator : compilationUnit.findAll(VariableDeclarator.class)) {
                if (isOldScriptResultVariable(variableDeclarator)) {
                    variableDeclarator.setType("Object");
                    changed = true;
                }
            }
            for (ObjectCreationExpr objectCreationExpr : compilationUnit.findAll(ObjectCreationExpr.class)) {
                if (isOldScriptResultConstruction(objectCreationExpr)) {
                    objectCreationExpr.replace(objectCreationExpr.getArgument(0).clone());
                    changed = true;
                }
            }
            for (MethodCallExpr methodCallExpr : compilationUnit.findAll(MethodCallExpr.class)) {
                if (isJavaScriptResultAccess(methodCallExpr)) {
                    methodCallExpr.replace(methodCallExpr.getScope().get().clone());
                    changed = true;
                }
            }
            return changed;
        }

        private boolean isOldScriptResultConstruction(final ObjectCreationExpr objectCreationExpr) {
            return objectCreationExpr.getType().asString().equals(OLD_SCRIPT_RESULT)
                    || objectCreationExpr.getType().getNameAsString().equals("ScriptResult");
        }

        private boolean isOldScriptResultVariable(final VariableDeclarator variableDeclarator) {
            return variableDeclarator.getType().asString().equals(OLD_SCRIPT_RESULT)
                    || variableDeclarator.getType().asString().equals("ScriptResult");
        }

        private boolean isJavaScriptResultAccess(final MethodCallExpr methodCallExpr) {
            if (!OLD_METHOD.equals(methodCallExpr.getNameAsString()) || methodCallExpr.getArguments().size() != 0) {
                return false;
            }
            return methodCallExpr.getScope().isPresent();
        }
    }
}
