package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ThisExpr;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.stream.Stream;

public class Main {
    public static void main(String[] args) {
        if (args.length != 1) {
            throw new IllegalArgumentException("Usage: Main <source-root>");
        }

        Path sourceRoot = Paths.get(args[0]);
        if (!Files.isDirectory(sourceRoot)) {
            throw new IllegalArgumentException("Not a directory: " + sourceRoot);
        }

        ParserConfiguration configuration = new ParserConfiguration();
        JavaParser parser = new JavaParser(configuration);

        try (Stream<Path> paths = Files.walk(sourceRoot)) {
            paths.filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                .forEach(path -> rewriteFile(parser, path));
        } catch (IOException e) {
            throw new RuntimeException("Failed to scan " + sourceRoot, e);
        }
    }

    private static void rewriteFile(JavaParser parser, Path path) {
        try {
            CompilationUnit cu = parser.parse(path, StandardCharsets.UTF_8).getResult()
                .orElseThrow(() -> new IllegalStateException("Unable to parse " + path));
            LexicalPreservingPrinter.setup(cu);
            cu.accept(new DependencyGraphFixer(), null);
            Files.writeString(path, LexicalPreservingPrinter.print(cu), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("Failed to rewrite " + path, e);
        }
    }

    private static final class DependencyGraphFixer extends ModifierVisitor<Void> {
        @Override
        public MethodCallExpr visit(MethodCallExpr n, Void arg) {
            super.visit(n, arg);

            if (!"buildDependencyGraph".equals(n.getNameAsString()) || n.getArguments().size() != 2) {
                return n;
            }

            if (!looksLikeOldApiCall(n.getArgument(0))) {
                return n;
            }

            findProjectBuildingRequestExpression(n).ifPresent(expression -> n.setArgument(0, expression));
            return n;
        }

        private boolean looksLikeOldApiCall(Expression expression) {
            String text = expression.toString();
            return text.endsWith("project") || text.equals("project") || text.contains("MavenProject");
        }

        private Optional<Expression> findProjectBuildingRequestExpression(MethodCallExpr call) {
            Optional<MethodDeclaration> method = call.findAncestor(MethodDeclaration.class);
            Optional<ConstructorDeclaration> constructor = call.findAncestor(ConstructorDeclaration.class);
            Optional<ClassOrInterfaceDeclaration> type = call.findAncestor(ClassOrInterfaceDeclaration.class);

            if (method.isPresent()) {
                Optional<Expression> param = findTypedParameter(method.get().getParameters());
                if (param.isPresent()) return param;
                Optional<Expression> local = findTypedLocalVariable(method.get());
                if (local.isPresent()) return local;
            }

            if (constructor.isPresent()) {
                Optional<Expression> param = findTypedParameter(constructor.get().getParameters());
                if (param.isPresent()) return param;
                Optional<Expression> local = findTypedLocalVariable(constructor.get());
                if (local.isPresent()) return local;
            }

            if (type.isPresent()) {
                Optional<Expression> field = findTypedField(type.get());
                if (field.isPresent()) return field;
            }

            if (call.findAncestor(ClassOrInterfaceDeclaration.class)
                .flatMap(c -> c.getFields().stream()
                    .filter(f -> hasType(f, "ProjectBuildingRequest"))
                    .findFirst())
                .isPresent()) {
                return Optional.of(new FieldAccessExpr(new ThisExpr(), "buildingRequest"));
            }

            if (call.findAncestor(MethodDeclaration.class)
                .flatMap(m -> m.getParameters().stream().filter(p -> p.getNameAsString().equals("buildingRequest")).findFirst())
                .isPresent()) {
                return Optional.of(new NameExpr("buildingRequest"));
            }

            if (call.findAncestor(MethodDeclaration.class)
                .flatMap(m -> m.getParameters().stream().filter(p -> p.getNameAsString().equals("request")).findFirst())
                .isPresent()) {
                return Optional.of(new NameExpr("request"));
            }

            if (call.findAncestor(MethodDeclaration.class)
                .flatMap(m -> m.getParameters().stream().filter(p -> p.getNameAsString().equals("session")).findFirst())
                .isPresent()) {
                return Optional.of(new MethodCallExpr(new NameExpr("session"), "getProjectBuildingRequest"));
            }

            return Optional.empty();
        }

        private Optional<Expression> findTypedParameter(Iterable<Parameter> parameters) {
            for (Parameter parameter : parameters) {
                if (typeMatches(parameter.getType().toString(), "ProjectBuildingRequest")) {
                    return Optional.of((Expression) new NameExpr(parameter.getNameAsString()));
                }
            }
            return Optional.empty();
        }

        private Optional<Expression> findTypedField(ClassOrInterfaceDeclaration type) {
            return type.getFields().stream()
                .filter(field -> hasType(field, "ProjectBuildingRequest"))
                .flatMap(field -> field.getVariables().stream())
                .map(variable -> (Expression) fieldAccessOrName(variable.getNameAsString(), type))
                .findFirst();
        }

        private Optional<Expression> findTypedLocalVariable(MethodDeclaration method) {
            return method.findAll(VariableDeclarationExpr.class).stream()
                .filter(expr -> typeMatches(expr.getElementType().toString(), "ProjectBuildingRequest"))
                .flatMap(expr -> expr.getVariables().stream())
                .map(variable -> (Expression) new NameExpr(variable.getNameAsString()))
                .findFirst();
        }

        private Optional<Expression> findTypedLocalVariable(ConstructorDeclaration constructor) {
            return constructor.findAll(VariableDeclarationExpr.class).stream()
                .filter(expr -> typeMatches(expr.getElementType().toString(), "ProjectBuildingRequest"))
                .flatMap(expr -> expr.getVariables().stream())
                .map(variable -> (Expression) new NameExpr(variable.getNameAsString()))
                .findFirst();
        }

        private boolean hasType(FieldDeclaration field, String simpleName) {
            return typeMatches(field.getElementType().toString(), simpleName);
        }

        private Expression fieldAccessOrName(String name, ClassOrInterfaceDeclaration type) {
            boolean instanceField = type.getFields().stream()
                .flatMap(field -> field.getVariables().stream())
                .anyMatch(variable -> variable.getNameAsString().equals(name));
            return instanceField ? new FieldAccessExpr(new ThisExpr(), name) : new NameExpr(name);
        }

        private boolean typeMatches(String typeName, String simpleName) {
            return typeName.equals(simpleName) || typeName.endsWith("." + simpleName) || typeName.endsWith(simpleName);
        }
    }
}
