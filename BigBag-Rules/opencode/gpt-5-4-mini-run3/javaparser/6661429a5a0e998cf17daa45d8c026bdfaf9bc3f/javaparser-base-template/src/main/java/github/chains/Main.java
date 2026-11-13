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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;

public class Main {
    private static final String OLD_TYPE = "com.gargoylesoftware.htmlunit.ScriptResult";
    private static final String SIMPLE_TYPE = "ScriptResult";

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            throw new IllegalArgumentException("Expected a single source directory argument");
        }

        Path sourceRoot = Paths.get(args[0]);
        try (var paths = Files.walk(sourceRoot)) {
            paths.filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                    .forEach(Main::rewriteFileSafely);
        }
    }

    private static void rewriteFileSafely(Path file) {
        try {
            rewriteFile(file);
        }
        catch (IOException exception) {
            throw new IllegalStateException("Failed to rewrite " + file, exception);
        }
    }

    private static void rewriteFile(Path file) throws IOException {
        CompilationUnit compilationUnit = StaticJavaParser.parse(file);
        LexicalPreservingPrinter.setup(compilationUnit);

        Set<String> wrappedVariables = new HashSet<>();
        boolean changed = false;

        for (VariableDeclarator variable : compilationUnit.findAll(VariableDeclarator.class)) {
            if (!isScriptResultType(variable.getType().asString()) || !variable.getInitializer().isPresent()) {
                continue;
            }

            Expression initializer = variable.getInitializer().get();
            if (!isScriptResultConstruction(initializer)) {
                continue;
            }

            ObjectCreationExpr creationExpr = initializer.asObjectCreationExpr();
            variable.setType("Object");
            variable.setInitializer(creationExpr.getArgument(0).clone());
            wrappedVariables.add(variable.getNameAsString());
            changed = true;
        }

        for (MethodCallExpr methodCall : compilationUnit.findAll(MethodCallExpr.class)) {
            if (!"getJavaScriptResult".equals(methodCall.getNameAsString()) || !methodCall.getScope().isPresent()) {
                continue;
            }

            Expression scope = methodCall.getScope().get();
            if (isScriptResultConstruction(scope)) {
                methodCall.replace(scope.asObjectCreationExpr().getArgument(0).clone());
                changed = true;
                continue;
            }

            if (scope.isNameExpr() && wrappedVariables.contains(scope.asNameExpr().getNameAsString())) {
                methodCall.replace(scope.clone());
                changed = true;
            }
        }

        if (changed) {
            compilationUnit.findAll(ImportDeclaration.class).forEach(importDeclaration -> {
                if (OLD_TYPE.equals(importDeclaration.getNameAsString())) {
                    importDeclaration.remove();
                }
            });

            String output = LexicalPreservingPrinter.print(compilationUnit);
            Files.writeString(file, output, StandardCharsets.UTF_8);
        }
    }

    private static boolean isScriptResultType(String typeName) {
        return SIMPLE_TYPE.equals(typeName) || OLD_TYPE.equals(typeName);
    }

    private static boolean isScriptResultConstruction(Expression expression) {
        return expression.isObjectCreationExpr() && isScriptResultType(expression.asObjectCreationExpr().getType().asString());
    }
}
