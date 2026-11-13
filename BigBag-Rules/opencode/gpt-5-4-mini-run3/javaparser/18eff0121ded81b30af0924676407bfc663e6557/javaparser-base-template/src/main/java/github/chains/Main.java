package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

public class Main {
    private static final String OLD_SCRIPT_RESULT = "com.gargoylesoftware.htmlunit.ScriptResult";
    private static final String OLD_METHOD = "getJavaScriptResult";

    public static void main(String[] args) {
        if (args.length == 0) {
            throw new IllegalArgumentException("Usage: Main <source-directory>");
        }

        Path sourceRoot = Paths.get(args[0]);
        if (!Files.isDirectory(sourceRoot)) {
            throw new IllegalArgumentException("Not a directory: " + sourceRoot);
        }

        ParserConfiguration configuration = new ParserConfiguration();
        JavaParser parser = new JavaParser(configuration);

        try {
            Files.walk(sourceRoot)
                    .filter(path -> path.toString().endsWith(".java"))
                    .forEach(path -> transform(parser, path));
        }
        catch (IOException exception) {
            throw new RuntimeException(exception);
        }
    }

    private static void transform(JavaParser parser, Path path) {
        try {
            Optional<CompilationUnit> parsed = parser.parse(path).getResult();
            if (!parsed.isPresent()) {
                return;
            }

            CompilationUnit compilationUnit = parsed.get();
            LexicalPreservingPrinter.setup(compilationUnit);

            Set<String> convertedVariables = new HashSet<>();
            compilationUnit.accept(new ModifierVisitor<Set<String>>() {
                @Override
                public Visitable visit(VariableDeclarator variableDeclarator, Set<String> variables) {
                    if (variableDeclarator.getInitializer().isPresent()) {
                        Expression initializer = variableDeclarator.getInitializer().get();
                        if (isScriptResultConstruction(initializer)) {
                            ObjectCreationExpr creation = initializer.asObjectCreationExpr();
                            Expression replacement = creation.getArgument(0).clone();
                            variableDeclarator.setInitializer(replacement);
                            variableDeclarator.setType(new ClassOrInterfaceType(null, "Object"));
                            variables.add(variableDeclarator.getNameAsString());
                        }
                    }
                    return super.visit(variableDeclarator, variables);
                }

                @Override
                public Visitable visit(MethodCallExpr methodCallExpr, Set<String> variables) {
                    if (OLD_METHOD.equals(methodCallExpr.getNameAsString()) && methodCallExpr.getScope().isPresent()) {
                        Expression scope = methodCallExpr.getScope().get();
                        if (scope.isObjectCreationExpr() && isScriptResultType(scope.asObjectCreationExpr())) {
                            return scope.asObjectCreationExpr().getArgument(0).clone();
                        }
                        if (scope.isNameExpr() && variables.contains(scope.asNameExpr().getNameAsString())) {
                            return scope.clone();
                        }
                    }
                    return super.visit(methodCallExpr, variables);
                }

                @Override
                public Visitable visit(CompilationUnit unit, Set<String> variables) {
                    Visitable visited = super.visit(unit, variables);
                    unit.getImports().removeIf(importDeclaration -> importDeclaration.getNameAsString().equals(OLD_SCRIPT_RESULT));
                    return visited;
                }
            }, convertedVariables);

            String updated = LexicalPreservingPrinter.print(compilationUnit);
            if (!updated.equals(Files.readString(path, StandardCharsets.UTF_8))) {
                Files.writeString(path, updated, StandardCharsets.UTF_8);
            }
        }
        catch (IOException exception) {
            throw new RuntimeException(exception);
        }
    }

    private static boolean isScriptResultConstruction(Expression expression) {
        return expression.isObjectCreationExpr() && isScriptResultType(expression.asObjectCreationExpr());
    }

    private static boolean isScriptResultType(ObjectCreationExpr creationExpr) {
        return creationExpr.getType().asString().equals("ScriptResult");
    }
}
