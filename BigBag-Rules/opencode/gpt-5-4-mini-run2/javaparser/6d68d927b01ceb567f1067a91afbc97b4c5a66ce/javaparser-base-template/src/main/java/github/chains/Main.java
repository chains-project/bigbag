package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;

public class Main {
    private static final String OLD_SCRIPT_RESULT = "com.gargoylesoftware.htmlunit.ScriptResult";
    private static final String OLD_SIMPLE_NAME = "ScriptResult";
    private static final String OBJECT_TYPE = "Object";

    public static void main(String[] args) throws IOException {
        Path root = args.length > 0 ? Paths.get(args[0]) : Paths.get(".");
        if (!Files.isDirectory(root)) {
            throw new IllegalArgumentException("Source directory does not exist: " + root);
        }

        Files.walk(root)
                .filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                .forEach(Main::rewriteFile);
    }

    private static void rewriteFile(Path path) {
        try {
            CompilationUnit compilationUnit = StaticJavaParser.parse(path, StandardCharsets.UTF_8);
            LexicalPreservingPrinter.setup(compilationUnit);

            boolean usesOldScriptResult = compilationUnit.getImports().stream()
                    .map(ImportDeclaration::getNameAsString)
                    .anyMatch(OLD_SCRIPT_RESULT::equals)
                    || compilationUnit.toString().contains(OLD_SCRIPT_RESULT);

            if (!usesOldScriptResult) {
                return;
            }

            compilationUnit.getImports().removeIf(importDeclaration -> importDeclaration.getNameAsString().equals(OLD_SCRIPT_RESULT));

            List<VariableDeclarator> variables = List.copyOf(compilationUnit.findAll(VariableDeclarator.class));
            variables.stream()
                    .filter(Main::hasOldScriptResultInitializer)
                    .forEach(Main::rewriteScriptResultVariable);

            List<MethodCallExpr> methodCalls = List.copyOf(compilationUnit.findAll(MethodCallExpr.class));
            methodCalls.stream()
                    .filter(Main::isJavaScriptResultGetter)
                    .forEach(Main::replaceJavaScriptResultGetter);

            List<Type> types = List.copyOf(compilationUnit.findAll(Type.class));
            types.stream()
                    .filter(Main::isOldScriptResultType)
                    .forEach(type -> type.replace(StaticJavaParser.parseType(OBJECT_TYPE)));

            Files.writeString(path, LexicalPreservingPrinter.print(compilationUnit), StandardCharsets.UTF_8);
        }
        catch (Exception exception) {
            throw new IllegalStateException("Failed to rewrite " + path, exception);
        }
    }

    private static boolean isOldScriptResultCreation(ObjectCreationExpr expression) {
        String typeName = expression.getType().asString();
        return OLD_SIMPLE_NAME.equals(typeName) || OLD_SCRIPT_RESULT.equals(typeName);
    }

    private static boolean hasOldScriptResultInitializer(VariableDeclarator variableDeclarator) {
        return variableDeclarator.getInitializer()
                .filter(Expression::isObjectCreationExpr)
                .map(Expression::asObjectCreationExpr)
                .filter(Main::isOldScriptResultCreation)
                .isPresent();
    }

    private static void rewriteScriptResultVariable(VariableDeclarator variableDeclarator) {
        Expression initializer = variableDeclarator.getInitializer().orElseThrow();
        ObjectCreationExpr creationExpr = initializer.asObjectCreationExpr();
        if (creationExpr.getArguments().isEmpty()) {
            return;
        }

        variableDeclarator.setType(StaticJavaParser.parseType(OBJECT_TYPE));
        variableDeclarator.setInitializer(creationExpr.getArgument(0).clone());
    }

    private static boolean isJavaScriptResultGetter(MethodCallExpr expression) {
        return "getJavaScriptResult".equals(expression.getNameAsString()) && expression.getArguments().isEmpty();
    }

    private static void replaceJavaScriptResultGetter(MethodCallExpr expression) {
        Expression scope = expression.getScope().orElseThrow();
        Expression replacement = scope.isObjectCreationExpr() && !scope.asObjectCreationExpr().getArguments().isEmpty()
                ? scope.asObjectCreationExpr().getArgument(0).clone()
                : scope.clone();

        expression.replace(replacement);
    }

    private static boolean isOldScriptResultType(Type type) {
        String typeName = type.asString();
        return OLD_SIMPLE_NAME.equals(typeName) || OLD_SCRIPT_RESULT.equals(typeName);
    }
}
