package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

public class Main {
    private static final String OLD_TYPE = "com.gargoylesoftware.htmlunit.ScriptResult";

    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Usage: Main <source-root>");
            System.exit(1);
        }

        Path sourceRoot = Paths.get(args[0]);
        if (!Files.isDirectory(sourceRoot)) {
            System.err.println("Source root does not exist or is not a directory: " + sourceRoot);
            System.exit(1);
        }

        try {
            Files.walk(sourceRoot)
                    .filter(path -> path.toString().endsWith(".java"))
                    .forEach(Main::rewriteFile);
        }
        catch (IOException exception) {
            throw new RuntimeException(exception);
        }
    }

    private static void rewriteFile(Path file) {
        try {
            CompilationUnit compilationUnit = StaticJavaParser.parse(file);
            LexicalPreservingPrinter.setup(compilationUnit);

            boolean[] changed = {false};

            changed[0] = compilationUnit.getImports().removeIf(importDeclaration ->
                    importDeclaration.getNameAsString().equals(OLD_TYPE));

            compilationUnit.accept(new ModifierVisitor<Boolean>() {
                @Override
                public Visitable visit(MethodCallExpr methodCallExpr, Boolean unused) {
                    if (isOldJavaScriptResultAccess(methodCallExpr)) {
                        changed[0] = true;
                        return rewrite(methodCallExpr);
                    }
                    return super.visit(methodCallExpr, unused);
                }
            }, Boolean.TRUE);

            if (changed[0]) {
                Files.writeString(file, LexicalPreservingPrinter.print(compilationUnit));
            }
        }
        catch (Exception exception) {
            throw new RuntimeException("Failed to rewrite " + file, exception);
        }
    }

    private static Expression rewrite(MethodCallExpr methodCallExpr) {
        return methodCallExpr.getScope()
                .filter(Expression::isObjectCreationExpr)
                .map(Expression::asObjectCreationExpr)
                .map(objectCreationExpr -> objectCreationExpr.getArguments().get(0).clone())
                .orElse(methodCallExpr);
    }

    private static boolean isOldJavaScriptResultAccess(MethodCallExpr methodCallExpr) {
        if (!"getJavaScriptResult".equals(methodCallExpr.getNameAsString())) {
            return false;
        }

        Optional<Expression> scope = methodCallExpr.getScope();
        if (!scope.isPresent() || !scope.get().isObjectCreationExpr()) {
            return false;
        }

        ObjectCreationExpr objectCreationExpr = scope.get().asObjectCreationExpr();
        return isOldScriptResultType(objectCreationExpr) && objectCreationExpr.getArguments().size() == 1;
    }

    private static boolean isOldScriptResultType(ObjectCreationExpr objectCreationExpr) {
        String typeName = objectCreationExpr.getType().getNameAsString();
        if (!"ScriptResult".equals(typeName)) {
            return false;
        }

        return objectCreationExpr.getType().getScope()
                .map(Node::toString)
                .orElse("")
                .equals("com.gargoylesoftware.htmlunit");
    }
}
