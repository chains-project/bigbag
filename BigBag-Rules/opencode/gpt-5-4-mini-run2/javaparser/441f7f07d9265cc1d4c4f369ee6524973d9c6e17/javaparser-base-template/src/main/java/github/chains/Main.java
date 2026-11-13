package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class Main {
    private static final String OLD_SCRIPT_RESULT = "com.gargoylesoftware.htmlunit.ScriptResult";
    private static final String OLD_SIMPLE_NAME = "ScriptResult";
    private static final String OLD_METHOD = "getJavaScriptResult";

    public static void main(String[] args) {
        Path root = Paths.get(args.length > 0 ? args[0] : ".");

        try {
            Files.walk(root)
                    .filter(path -> path.toString().endsWith(".java"))
                    .forEach(Main::transformFile);
        }
        catch (IOException exception) {
            throw new RuntimeException(exception);
        }
    }

    private static void transformFile(Path file) {
        try {
            CompilationUnit compilationUnit = StaticJavaParser.parse(file);
            boolean[] changed = new boolean[] {false};

            compilationUnit.getImports().removeIf(importDeclaration -> {
                boolean match = OLD_SCRIPT_RESULT.equals(importDeclaration.getNameAsString());
                changed[0] |= match;
                return match;
            });

            List<ObjectCreationExpr> creations = compilationUnit.findAll(ObjectCreationExpr.class);
            for (int i = creations.size() - 1; i >= 0; i--) {
                ObjectCreationExpr creation = creations.get(i);
                if (isOldScriptResultType(creation) && creation.getArguments().size() == 1) {
                    creation.replace(creation.getArgument(0).clone());
                    changed[0] = true;
                }
            }

            List<MethodCallExpr> calls = compilationUnit.findAll(MethodCallExpr.class);
            for (int i = calls.size() - 1; i >= 0; i--) {
                MethodCallExpr call = calls.get(i);
                if (OLD_METHOD.equals(call.getNameAsString()) && call.getScope().isPresent()) {
                    call.replace(call.getScope().get().clone());
                    changed[0] = true;
                }
            }

            for (VariableDeclarator variableDeclarator : compilationUnit.findAll(VariableDeclarator.class)) {
                if (isOldScriptResultType(variableDeclarator.getType().asString())) {
                    variableDeclarator.setType("Object");
                    changed[0] = true;
                }
            }

            if (changed[0]) {
                Files.writeString(file, compilationUnit.toString() + System.lineSeparator(), StandardCharsets.UTF_8);
            }
        }
        catch (IOException exception) {
            throw new RuntimeException(exception);
        }
    }

    private static boolean isOldScriptResultType(ObjectCreationExpr creation) {
        String typeName = creation.getType().getNameAsString();
        return OLD_SIMPLE_NAME.equals(typeName) || OLD_SCRIPT_RESULT.equals(creation.getType().getNameWithScope());
    }

    private static boolean isOldScriptResultType(String typeName) {
        return OLD_SIMPLE_NAME.equals(typeName) || OLD_SCRIPT_RESULT.equals(typeName);
    }
}
