package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

public class Main {
    private static final String OLD_TYPE = "com.gargoylesoftware.htmlunit.ScriptResult";
    private static final String SIMPLE_OLD_TYPE = "ScriptResult";
    private static final String OLD_METHOD = "getJavaScriptResult";

    public static void main(String[] args) {
        if (args.length != 1) {
            throw new IllegalArgumentException("Expected exactly one argument: source directory");
        }

        Path sourceRoot = Paths.get(args[0]);
        if (!Files.isDirectory(sourceRoot)) {
            throw new IllegalArgumentException("Not a directory: " + sourceRoot);
        }

        try (Stream<Path> paths = Files.walk(sourceRoot)) {
            paths.filter(path -> path.toString().endsWith(".java"))
                    .forEach(Main::transformJavaFile);
        }
        catch (IOException exception) {
            throw new RuntimeException("Failed to traverse " + sourceRoot, exception);
        }
    }

    private static void transformJavaFile(Path path) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(path, StandardCharsets.UTF_8);
            LexicalPreservingPrinter.setup(cu);
            boolean changed = transformCompilationUnit(cu);
            if (changed) {
                Files.write(path, LexicalPreservingPrinter.print(cu).getBytes(StandardCharsets.UTF_8));
            }
        }
        catch (IOException exception) {
            throw new RuntimeException("Failed to process " + path, exception);
        }
    }

    private static boolean transformCompilationUnit(CompilationUnit cu) {
        boolean[] changed = {false};
        Map<String, Expression> replacements = new HashMap<>();

        cu.findAll(VariableDeclarator.class).forEach(variable -> {
            Optional<Expression> initializer = variable.getInitializer();
            if (!initializer.isPresent() || !isScriptResultCreation(initializer.get())) {
                return;
            }

            ObjectCreationExpr creation = initializer.get().asObjectCreationExpr();
            if (creation.getArguments().isEmpty()) {
                return;
            }

            Expression inner = creation.getArgument(0).clone();
            replacements.put(variable.getNameAsString(), inner);
            variable.setType(new ClassOrInterfaceType(null, "Object"));
            variable.setInitializer(inner.clone());
            changed[0] = true;
        });

        cu.findAll(MethodCallExpr.class).forEach(call -> {
            if (!OLD_METHOD.equals(call.getNameAsString()) || !call.getScope().isPresent()) {
                return;
            }

            Expression scope = call.getScope().get();
            if (scope.isObjectCreationExpr() && isScriptResultCreation(scope)) {
                ObjectCreationExpr creation = scope.asObjectCreationExpr();
                if (!creation.getArguments().isEmpty()) {
                    call.replace(creation.getArgument(0).clone());
                    changed[0] = true;
                }
                return;
            }

            if (scope.isNameExpr()) {
                String name = scope.asNameExpr().getNameAsString();
                Expression replacement = replacements.get(name);
                if (replacement != null) {
                    call.replace(replacement.clone());
                    changed[0] = true;
                }
            }
        });

        if (removeScriptResultImport(cu)) {
            changed[0] = true;
        }

        return changed[0];
    }

    private static boolean isScriptResultCreation(Expression expression) {
        if (!expression.isObjectCreationExpr()) {
            return false;
        }

        ObjectCreationExpr creation = expression.asObjectCreationExpr();
        String typeName = creation.getType().getNameAsString();
        String fullyQualifiedName = creation.getType().toString();
        return SIMPLE_OLD_TYPE.equals(typeName) || OLD_TYPE.equals(fullyQualifiedName);
    }

    private static boolean removeScriptResultImport(CompilationUnit cu) {
        List<Node> toRemove = new ArrayList<>();
        cu.getImports().forEach(importDecl -> {
            String name = importDecl.getNameAsString();
            if (OLD_TYPE.equals(name)) {
                toRemove.add(importDecl);
            }
        });

        toRemove.forEach(Node::remove);
        return !toRemove.isEmpty();
    }
}
