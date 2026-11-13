package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Main {
    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Usage: Main <source-root>");
            System.exit(1);
        }

        Path sourceRoot = Paths.get(args[0]);
        if (!Files.isDirectory(sourceRoot)) {
            System.err.println("Not a directory: " + sourceRoot);
            System.exit(1);
        }

        try {
            transform(sourceRoot);
        }
        catch (IOException exception) {
            System.err.println("Transformation failed: " + exception.getMessage());
            System.exit(1);
        }
    }

    private static void transform(final Path sourceRoot) throws IOException {
        try (var paths = Files.walk(sourceRoot)) {
            paths.filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                    .forEach(Main::transformFile);
        }
    }

    private static void transformFile(final Path file) {
        try {
            CompilationUnit compilationUnit = StaticJavaParser.parse(file);
            LexicalPreservingPrinter.setup(compilationUnit);

            boolean changed = false;
            changed |= replaceLegacyScriptResultTypes(compilationUnit);
            changed |= replaceLegacyScriptResultCreations(compilationUnit);
            changed |= replaceLegacyJavaScriptResultCalls(compilationUnit);
            changed |= replaceLegacyScriptResultUsage(compilationUnit);
            changed |= removeLegacyScriptResultImport(compilationUnit);

            if (changed) {
                Files.writeString(file, LexicalPreservingPrinter.print(compilationUnit));
            }
        }
        catch (IOException exception) {
            throw new RuntimeException("Failed to transform " + file + ": " + exception.getMessage(), exception);
        }
    }

    private static boolean replaceLegacyScriptResultUsage(final CompilationUnit compilationUnit) {
        boolean changed = false;
        for (MethodCallExpr methodCall : compilationUnit.findAll(MethodCallExpr.class)) {
            if (!"getJavaScriptResult".equals(methodCall.getNameAsString()) || methodCall.getScope().isEmpty()) {
                continue;
            }

            Expression scope = methodCall.getScope().get();
            if (!scope.isObjectCreationExpr()) {
                continue;
            }

            ObjectCreationExpr objectCreationExpr = scope.asObjectCreationExpr();
            if (!isLegacyScriptResultType(objectCreationExpr) || objectCreationExpr.getArguments().size() != 1) {
                continue;
            }

            methodCall.replace(objectCreationExpr.getArgument(0).clone());
            changed = true;
        }
        return changed;
    }

    private static boolean replaceLegacyScriptResultTypes(final CompilationUnit compilationUnit) {
        boolean changed = false;
        for (VariableDeclarator variableDeclarator : compilationUnit.findAll(VariableDeclarator.class)) {
            if (isLegacyScriptResultType(variableDeclarator.getType())) {
                variableDeclarator.setType("Object");
                changed = true;
            }
        }
        return changed;
    }

    private static boolean replaceLegacyScriptResultCreations(final CompilationUnit compilationUnit) {
        boolean changed = false;
        for (ObjectCreationExpr objectCreationExpr : compilationUnit.findAll(ObjectCreationExpr.class)) {
            if (!isLegacyScriptResultType(objectCreationExpr) || objectCreationExpr.getArguments().size() != 1) {
                continue;
            }

            objectCreationExpr.replace(objectCreationExpr.getArgument(0).clone());
            changed = true;
        }
        return changed;
    }

    private static boolean replaceLegacyJavaScriptResultCalls(final CompilationUnit compilationUnit) {
        boolean changed = false;
        for (MethodCallExpr methodCall : compilationUnit.findAll(MethodCallExpr.class)) {
            if (!"getJavaScriptResult".equals(methodCall.getNameAsString()) || methodCall.getScope().isEmpty()) {
                continue;
            }

            methodCall.replace(methodCall.getScope().get().clone());
            changed = true;
        }
        return changed;
    }

    private static boolean removeLegacyScriptResultImport(final CompilationUnit compilationUnit) {
        return compilationUnit.getImports().removeIf(importDeclaration ->
                importDeclaration.getNameAsString().equals("com.gargoylesoftware.htmlunit.ScriptResult"));
    }

    private static boolean isLegacyScriptResultType(final ObjectCreationExpr objectCreationExpr) {
        if (!"ScriptResult".equals(objectCreationExpr.getType().getNameAsString())) {
            return false;
        }

        if (objectCreationExpr.getType().isClassOrInterfaceType()) {
            ClassOrInterfaceType type = objectCreationExpr.getType().asClassOrInterfaceType();
            return !type.getScope().isPresent()
                    || "com.gargoylesoftware.htmlunit".equals(type.getScope().get().toString());
        }

        return true;
    }

    private static boolean isLegacyScriptResultType(final Type type) {
        if (!type.isClassOrInterfaceType()) {
            return false;
        }

        ClassOrInterfaceType classType = type.asClassOrInterfaceType();
        if (!"ScriptResult".equals(classType.getNameAsString())) {
            return false;
        }

        return !classType.getScope().isPresent()
                || "com.gargoylesoftware.htmlunit".equals(classType.getScope().get().toString());
    }
}
