package github.chains;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import com.github.javaparser.ast.type.Type;

public class Main {
    private static final String OLD_IMPORT = "com.gargoylesoftware.htmlunit.ScriptResult";
    private static final String OLD_TYPE = "ScriptResult";
    private static final String METHOD = "getJavaScriptResult";

    public static void main(String[] args) {
        if (args.length != 1) {
            throw new IllegalArgumentException("Expected one argument: source directory");
        }

        try {
            transform(Paths.get(args[0]));
        }
        catch (IOException exception) {
            throw new RuntimeException(exception);
        }
    }

    private static void transform(Path sourceRoot) throws IOException {
        try (var files = Files.walk(sourceRoot)) {
            files.filter(path -> path.toString().endsWith(".java"))
                    .forEach(Main::rewriteFile);
        }
    }

    private static void rewriteFile(Path file) {
        try {
            CompilationUnit compilationUnit = StaticJavaParser.parse(file);
            boolean changed = rewriteScriptResultUsage(compilationUnit);
            changed |= compilationUnit.getImports().removeIf(importDecl -> importDecl.getNameAsString().equals(OLD_IMPORT));

            if (changed) {
                Files.writeString(file, compilationUnit.toString(), StandardCharsets.UTF_8);
            }
        }
        catch (IOException exception) {
            throw new RuntimeException(exception);
        }
    }

    private static boolean rewriteScriptResultUsage(CompilationUnit compilationUnit) {
        Set<String> scriptResultVariables = new HashSet<>();
        boolean[] changed = {false};

        for (VariableDeclarator variableDeclarator : compilationUnit.findAll(VariableDeclarator.class)) {
            if (isOldScriptResultVariable(variableDeclarator)) {
                scriptResultVariables.add(variableDeclarator.getNameAsString());
                rewriteVariableDeclarator(variableDeclarator);
                changed[0] = true;
            }
        }

        compilationUnit.accept(new ModifierVisitor<Set<String>>() {
            @Override
            public Visitable visit(MethodCallExpr methodCallExpr, Set<String> names) {
                Visitable visited = super.visit(methodCallExpr, names);
                if (!(visited instanceof MethodCallExpr)) {
                    return visited;
                }

                MethodCallExpr current = (MethodCallExpr) visited;
                if (isOldScriptResultGetter(current, names)) {
                    changed[0] = true;
                    return current.getScope().get().clone();
                }
                if (isDirectScriptResultGetter(current)) {
                    changed[0] = true;
                    return current.getScope().get().asObjectCreationExpr().getArgument(0).clone();
                }
                return current;
            }

            @Override
            public Visitable visit(ObjectCreationExpr objectCreationExpr, Set<String> names) {
                Visitable visited = super.visit(objectCreationExpr, names);
                if (!(visited instanceof ObjectCreationExpr)) {
                    return visited;
                }

                ObjectCreationExpr current = (ObjectCreationExpr) visited;
                if (isOldScriptResultCreation(current)) {
                    changed[0] = true;
                    return current.getArgument(0).clone();
                }
                return current;
            }
        }, scriptResultVariables);

        return changed[0];
    }

    private static boolean isOldScriptResultVariable(VariableDeclarator variableDeclarator) {
        return isOldScriptResultType(variableDeclarator.getType())
                && variableDeclarator.getInitializer().isPresent()
                && variableDeclarator.getInitializer().get().isObjectCreationExpr()
                && isOldScriptResultCreation(variableDeclarator.getInitializer().get().asObjectCreationExpr());
    }

    private static void rewriteVariableDeclarator(VariableDeclarator variableDeclarator) {
        variableDeclarator.setType(StaticJavaParser.parseType("Object"));
        variableDeclarator.getInitializer().ifPresent(initializer ->
                variableDeclarator.setInitializer(initializer.asObjectCreationExpr().getArgument(0).clone()));
    }

    private static boolean isOldScriptResultGetter(MethodCallExpr methodCallExpr, Set<String> scriptResultVariables) {
        return methodCallExpr.getNameAsString().equals(METHOD)
                && methodCallExpr.getScope().isPresent()
                && methodCallExpr.getScope().get().isNameExpr()
                && scriptResultVariables.contains(methodCallExpr.getScope().get().asNameExpr().getNameAsString());
    }

    private static boolean isDirectScriptResultGetter(MethodCallExpr methodCallExpr) {
        return methodCallExpr.getNameAsString().equals(METHOD)
                && methodCallExpr.getScope().isPresent()
                && methodCallExpr.getScope().get().isObjectCreationExpr()
                && isOldScriptResultCreation(methodCallExpr.getScope().get().asObjectCreationExpr());
    }

    private static boolean isOldScriptResultCreation(ObjectCreationExpr objectCreationExpr) {
        return objectCreationExpr.getType().getNameAsString().equals(OLD_TYPE)
                && objectCreationExpr.getArguments().size() == 1;
    }

    private static boolean isOldScriptResultType(Type type) {
        return type.isClassOrInterfaceType() && type.asClassOrInterfaceType().getNameAsString().equals(OLD_TYPE);
    }
}
