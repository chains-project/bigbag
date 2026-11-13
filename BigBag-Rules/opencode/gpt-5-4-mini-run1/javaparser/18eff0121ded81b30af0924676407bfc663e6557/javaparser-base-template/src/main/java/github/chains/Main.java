package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.ThisExpr;
import com.github.javaparser.ast.type.Type;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import java.util.Optional;

public class Main {
    public static void main(String[] args) {
        if (args.length != 1) {
            throw new IllegalArgumentException("Usage: Main <source-directory>");
        }

        Path root = Paths.get(args[0]);
        if (!Files.isDirectory(root)) {
            throw new IllegalArgumentException("Not a directory: " + root);
        }

        try {
            Files.walk(root)
                    .filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                    .sorted(Comparator.naturalOrder())
                    .forEach(Main::transformFile);
        }
        catch (IOException exception) {
            throw new RuntimeException("Failed to transform sources", exception);
        }
    }

    private static void transformFile(Path path) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(path);
            boolean changed = false;
            changed |= removeOldImport(cu);
            changed |= rewriteScriptResultUsages(cu);

            if (changed) {
                Files.writeString(path, cu.toString(), StandardCharsets.UTF_8);
            }
        }
        catch (Exception exception) {
            throw new RuntimeException("Failed to transform " + path, exception);
        }
    }

    private static boolean removeOldImport(CompilationUnit cu) {
        return cu.getImports().removeIf(importDeclaration ->
                importDeclaration.getNameAsString().equals("com.gargoylesoftware.htmlunit.ScriptResult"));
    }

    private static boolean rewriteScriptResultUsages(CompilationUnit cu) {
        boolean changed = false;
        Set<String> oldScriptResultVariables = new HashSet<>();

        for (VariableDeclarator variable : cu.findAll(VariableDeclarator.class)) {
            if (isOldScriptResultType(variable.getType())) {
                oldScriptResultVariables.add(variable.getNameAsString());
                if (isOldScriptResultCreation(variable.getInitializer())) {
                    variable.getInitializer().ifPresent(initializer -> initializer.replace(((ObjectCreationExpr) initializer).getArgument(0).clone()));
                    variable.setType("Object");
                    changed = true;
                }
            }
        }

        for (MethodCallExpr call : cu.findAll(MethodCallExpr.class)) {
            if (!call.getNameAsString().equals("getJavaScriptResult")) {
                continue;
            }

            Optional<Expression> replacement = replacementFor(call);
            if (replacement.isPresent()) {
                call.replace(replacement.get().clone());
                changed = true;
            }
            else if (call.getScope().isPresent() && call.getScope().get() instanceof NameExpr) {
                String name = ((NameExpr) call.getScope().get()).getNameAsString();
                if (oldScriptResultVariables.contains(name)) {
                    call.replace(call.getScope().get().clone());
                    changed = true;
                }
            }
        }

        return changed;
    }

    private static Optional<Expression> replacementFor(MethodCallExpr call) {
        Optional<Expression> scope = call.getScope();
        if (scope.isEmpty()) {
            return Optional.empty();
        }

        Expression receiver = scope.get();
        if (receiver instanceof ObjectCreationExpr) {
            ObjectCreationExpr creation = (ObjectCreationExpr) receiver;
            if (isOldScriptResultCreation(creation)) {
                return firstArgument(creation);
            }
        }

        if (receiver instanceof NameExpr || receiver instanceof FieldAccessExpr) {
            Optional<VariableDeclarator> variable = findVariableDeclarator(call, receiver);
            if (variable.isPresent() && isOldScriptResultCreation(variable.get().getInitializer())) {
                return firstArgument((ObjectCreationExpr) variable.get().getInitializer().get());
            }
        }

        return Optional.empty();
    }

    private static Optional<VariableDeclarator> findVariableDeclarator(Node start, Expression receiver) {
        String name = variableName(receiver);
        if (name == null) {
            return Optional.empty();
        }

        return start.findAncestor(CompilationUnit.class)
                .flatMap(cu -> cu.findAll(VariableDeclarator.class).stream()
                        .filter(variable -> variable.getNameAsString().equals(name))
                        .findFirst());
    }

    private static String variableName(Expression receiver) {
        if (receiver instanceof NameExpr) {
            return ((NameExpr) receiver).getNameAsString();
        }
        if (receiver instanceof FieldAccessExpr) {
            FieldAccessExpr fieldAccessExpr = (FieldAccessExpr) receiver;
            if (fieldAccessExpr.getScope() instanceof ThisExpr) {
                return fieldAccessExpr.getNameAsString();
            }
        }
        return null;
    }

    private static boolean isOldScriptResultCreation(Optional<Expression> initializer) {
        return initializer.isPresent() && initializer.get() instanceof ObjectCreationExpr
                && isOldScriptResultCreation((ObjectCreationExpr) initializer.get());
    }

    private static boolean isOldScriptResultCreation(ObjectCreationExpr creation) {
        return isOldScriptResultType(creation.getType());
    }

    private static boolean isOldScriptResultType(Type type) {
        String text = type.asString();
        return text.equals("ScriptResult") || text.equals("com.gargoylesoftware.htmlunit.ScriptResult");
    }

    private static Optional<Expression> firstArgument(ObjectCreationExpr creation) {
        if (creation.getArguments().isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(creation.getArgument(0));
    }
}
