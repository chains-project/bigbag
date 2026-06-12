package github.chains;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.stream.Stream;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;
import com.github.javaparser.StaticJavaParser;

public class Main {
    private static final String OLD_TYPE = "com.gargoylesoftware.htmlunit.ScriptResult";
    private static final String OLD_SIMPLE_NAME = "ScriptResult";
    private static final String OLD_METHOD = "getJavaScriptResult";

    public static void main(String[] args) {
        if (args.length != 1) {
            throw new IllegalArgumentException("Expected exactly one argument: source directory");
        }

        Path sourceDir = Paths.get(args[0]);
        try (Stream<Path> paths = Files.walk(sourceDir)) {
            paths.filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                    .sorted(Comparator.naturalOrder())
                    .forEach(Main::rewrite);
        }
        catch (IOException exception) {
            throw new RuntimeException(exception);
        }
    }

    private static void rewrite(Path file) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(file);
            LexicalPreservingPrinter.setup(cu);

            cu.getImports().removeIf(Main::isOldImport);
            cu.findAll(ObjectCreationExpr.class).forEach(Main::rewriteCreation);
            cu.findAll(MethodCallExpr.class).forEach(Main::rewriteMethodCall);

            Files.writeString(file, LexicalPreservingPrinter.print(cu), StandardCharsets.UTF_8);
        }
        catch (IOException exception) {
            throw new RuntimeException("Failed to rewrite " + file, exception);
        }
    }

    private static void rewriteMethodCall(MethodCallExpr call) {
        if (!OLD_METHOD.equals(call.getNameAsString()) || call.getScope().isEmpty()) {
            return;
        }

        Expression scope = call.getScope().get();
        if (scope instanceof ObjectCreationExpr) {
            ObjectCreationExpr creation = (ObjectCreationExpr) scope;
            if (isOldType(creation.getType()) && !creation.getArguments().isEmpty()) {
                call.replace(creation.getArgument(0).clone());
            }
        }
    }

    private static void rewriteCreation(ObjectCreationExpr creation) {
        if (creation.getArguments().isEmpty() || !isOldType(creation.getType())) {
            return;
        }

        creation.replace(creation.getArgument(0).clone());
    }

    private static boolean isOldType(Type type) {
        return OLD_SIMPLE_NAME.equals(type.asString()) || OLD_TYPE.equals(type.asString());
    }

    private static boolean isOldImport(ImportDeclaration importDeclaration) {
        return !importDeclaration.isAsterisk() && OLD_TYPE.equals(importDeclaration.getNameAsString());
    }
}
