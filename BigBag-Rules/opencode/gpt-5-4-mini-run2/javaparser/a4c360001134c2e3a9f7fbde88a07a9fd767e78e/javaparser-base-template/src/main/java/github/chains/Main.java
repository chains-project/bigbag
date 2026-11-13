package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.EnclosedExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
import com.github.javaparser.ast.visitor.Visitable;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

public class Main {
    private static final String FILTER_BUILDER = "FilterBuilder";
    private static final Set<String> FILTER_BUILDER_RETURNING_METHODS = Set.of(
            "add",
            "exclude",
            "excludePackage",
            "excludePattern",
            "include",
            "includePackage",
            "includePattern",
            "parsePackages"
    );

    public static void main(String[] args) {
        if (args.length != 1) {
            throw new IllegalArgumentException("Usage: Main <source-root>");
        }

        Path sourceRoot = Paths.get(args[0]);
        if (!Files.isDirectory(sourceRoot)) {
            throw new IllegalArgumentException("Source root does not exist: " + sourceRoot);
        }

        ParserConfiguration configuration = new ParserConfiguration();
        JavaParser parser = new JavaParser(configuration);

        try (Stream<Path> paths = Files.walk(sourceRoot)) {
            paths.filter(path -> path.toString().endsWith(".java"))
                    .forEach(path -> rewriteFile(parser, path));
        } catch (IOException e) {
            throw new RuntimeException("Failed to traverse source root " + sourceRoot, e);
        }
    }

    private static void rewriteFile(JavaParser parser, Path file) {
        try {
            CompilationUnit compilationUnit = parser.parse(file).getResult()
                    .orElseThrow(() -> new IllegalStateException("Could not parse " + file));

            LexicalPreservingPrinter.setup(compilationUnit);
            Set<String> filterBuilderVariables = collectFilterBuilderVariables(compilationUnit);
            boolean[] changed = {false};

            compilationUnit.accept(new ModifierVisitor<Set<String>>() {
                @Override
                public Visitable visit(MethodCallExpr n, Set<String> variables) {
                    if (shouldRenameApplyToTest(n, variables)) {
                        n.setName("test");
                        changed[0] = true;
                    }
                    return super.visit(n, variables);
                }
            }, filterBuilderVariables);

            if (changed[0]) {
                Files.write(file, LexicalPreservingPrinter.print(compilationUnit).getBytes(StandardCharsets.UTF_8));
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to rewrite " + file, e);
        }
    }

    private static Set<String> collectFilterBuilderVariables(CompilationUnit compilationUnit) {
        Set<String> variables = new HashSet<>();
        compilationUnit.findAll(VariableDeclarationExpr.class).forEach(expr -> expr.getVariables().forEach(variable -> {
            String typeName = variable.getType().asString();
            if (isFilterBuilderTypeName(typeName)) {
                variables.add(variable.getNameAsString());
            }
        }));
        compilationUnit.findAll(com.github.javaparser.ast.body.Parameter.class).forEach(parameter -> {
            if (isFilterBuilderTypeName(parameter.getType().asString())) {
                variables.add(parameter.getNameAsString());
            }
        });
        compilationUnit.findAll(com.github.javaparser.ast.body.FieldDeclaration.class).forEach(field -> {
            if (isFilterBuilderTypeName(field.getElementType().asString())) {
                field.getVariables().forEach(variable -> variables.add(variable.getNameAsString()));
            }
        });
        return variables;
    }

    private static boolean shouldRenameApplyToTest(MethodCallExpr call, Set<String> filterBuilderVariables) {
        if (!"apply".equals(call.getNameAsString()) || call.getArguments().size() != 1) {
            return false;
        }
        return isFilterBuilderExpression(call.getScope().orElse(null), filterBuilderVariables);
    }

    private static boolean isFilterBuilderExpression(Expression expression, Set<String> filterBuilderVariables) {
        if (expression == null) {
            return false;
        }
        if (expression.isEnclosedExpr()) {
            return isFilterBuilderExpression(expression.asEnclosedExpr().getInner(), filterBuilderVariables);
        }
        if (expression.isObjectCreationExpr()) {
            return isFilterBuilderTypeName(expression.asObjectCreationExpr().getType().asString());
        }
        if (expression.isNameExpr()) {
            String name = expression.asNameExpr().getNameAsString();
            return filterBuilderVariables.contains(name) || FILTER_BUILDER.equals(name);
        }
        if (expression.isFieldAccessExpr()) {
            FieldAccessExpr fieldAccess = expression.asFieldAccessExpr();
            return isFilterBuilderExpression(fieldAccess.getScope(), filterBuilderVariables)
                    || isFilterBuilderTypeName(fieldAccess.getNameAsString());
        }
        if (expression.isMethodCallExpr()) {
            MethodCallExpr methodCall = expression.asMethodCallExpr();
            if (FILTER_BUILDER_RETURNING_METHODS.contains(methodCall.getNameAsString())) {
                return methodCall.getScope().isPresent()
                        ? isFilterBuilderExpression(methodCall.getScope().get(), filterBuilderVariables)
                        : true;
            }
            return isFilterBuilderExpression(methodCall.getScope().orElse(null), filterBuilderVariables);
        }
        if (expression.isCastExpr()) {
            return isFilterBuilderTypeName(expression.asCastExpr().getType().asString())
                    || isFilterBuilderExpression(expression.asCastExpr().getExpression(), filterBuilderVariables);
        }
        return false;
    }

    private static boolean isFilterBuilderTypeName(String typeName) {
        return typeName != null && (FILTER_BUILDER.equals(typeName) || typeName.endsWith("." + FILTER_BUILDER));
    }
}
