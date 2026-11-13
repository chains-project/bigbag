package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.visitor.Visitable;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

public class Main {
    private static final String OLD_SCRIPT_RESULT_TYPE = "com.gargoylesoftware.htmlunit.ScriptResult";
    private static final String OLD_SCRIPT_RESULT_SIMPLE = "ScriptResult";

    public static void main(String[] args) {
        Path root = args.length > 0 ? Paths.get(args[0]) : Paths.get(".");
        try {
            transform(root);
        }
        catch (IOException exception) {
            throw new RuntimeException(exception);
        }
    }

    private static void transform(Path root) throws IOException {
        try (Stream<Path> paths = Files.walk(root)) {
            paths.filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                    .forEach(Main::transformFileSafely);
        }
    }

    private static void transformFileSafely(Path file) {
        try {
            transformFile(file);
        }
        catch (IOException exception) {
            throw new RuntimeException("Failed to transform " + file, exception);
        }
    }

    private static void transformFile(Path file) throws IOException {
        CompilationUnit compilationUnit = StaticJavaParser.parse(file, StandardCharsets.UTF_8);
        LexicalPreservingPrinter.setup(compilationUnit);

        compilationUnit.accept(new ModifierVisitor<Void>() {
            @Override
            public Visitable visit(VariableDeclarator variableDeclarator, Void arg) {
                if (isOldJavaScriptResultType(variableDeclarator.getType().asString())) {
                    variableDeclarator.setType("Object");
                    return variableDeclarator;
                }
                return super.visit(variableDeclarator, arg);
            }

            @Override
            public Visitable visit(MethodCallExpr call, Void arg) {
                if ("getJavaScriptResult".equals(call.getNameAsString()) && call.getScope().isPresent()) {
                    Expression scope = call.getScope().get();
                    if (scope.isObjectCreationExpr()) {
                        ObjectCreationExpr creation = scope.asObjectCreationExpr();
                        if (isOldJavaScriptResultCreation(creation) && !creation.getArguments().isEmpty()) {
                            return creation.getArgument(0).clone();
                        }
                    }
                    return scope.clone();
                }
                return super.visit(call, arg);
            }

            @Override
            public Visitable visit(ObjectCreationExpr creation, Void arg) {
                if (isOldJavaScriptResultCreation(creation) && !creation.getArguments().isEmpty()) {
                    return creation.getArgument(0).clone();
                }
                return super.visit(creation, arg);
            }
        }, null);

        compilationUnit.getImports().removeIf(importDeclaration ->
                OLD_SCRIPT_RESULT_TYPE.equals(importDeclaration.getNameAsString()));

        Files.write(file, LexicalPreservingPrinter.print(compilationUnit).getBytes(StandardCharsets.UTF_8));
    }

    private static boolean isOldJavaScriptResultCreation(ObjectCreationExpr creation) {
        return isOldJavaScriptResultType(creation.getType().asString())
                || OLD_SCRIPT_RESULT_SIMPLE.equals(creation.getType().getNameAsString())
                || OLD_SCRIPT_RESULT_TYPE.equals(creation.getType().asString());
    }

    private static boolean isOldJavaScriptResultType(String typeName) {
        return OLD_SCRIPT_RESULT_SIMPLE.equals(typeName) || OLD_SCRIPT_RESULT_TYPE.equals(typeName);
    }
}
