package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ThisExpr;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
import com.github.javaparser.ast.stmt.BlockStmt;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public class Main {

    public static void main(String[] args) {
        Path sourceRoot = Paths.get(args.length > 0 ? args[0] : ".");
        int changedFiles = 0;

        try {
            List<Path> javaFiles = new ArrayList<>();
            try (var stream = Files.walk(sourceRoot)) {
                stream.filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                        .forEach(javaFiles::add);
            }

            for (Path file : javaFiles) {
                CompilationUnit cu = StaticJavaParser.parse(file);
                boolean[] modified = {false};

                cu.findAll(MethodCallExpr.class).forEach(call -> {
                    if (!isBuildDependencyGraphCall(call)) {
                        return;
                    }

                    Optional<Expression> replacement = findProjectBuildingRequestExpression(call);
                    replacement.ifPresent(expression -> {
                        call.setArgument(0, expression);
                        modified[0] = true;
                    });
                });

                if (modified[0]) {
                    Files.writeString(file, cu.toString());
                    changedFiles++;
                }
            }

            System.out.println("Updated files: " + changedFiles);
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    private static boolean isBuildDependencyGraphCall(MethodCallExpr call) {
        return call.getNameAsString().equals("buildDependencyGraph") && call.getArguments().size() == 2;
    }

    private static Optional<Expression> findProjectBuildingRequestExpression(MethodCallExpr call) {
        List<Expression> candidates = new ArrayList<>();

        call.findAncestor(ClassOrInterfaceDeclaration.class).ifPresent(type -> {
            for (FieldDeclaration field : type.getFields()) {
                if (isTypeName(field.getElementType().asString(), "ProjectBuildingRequest")) {
                    VariableDeclarator variable = field.getVariables().get(0);
                    candidates.add(new FieldAccessExpr(new ThisExpr(), variable.getNameAsString()));
                }
            }

            for (MethodDeclaration method : type.getMethods()) {
                if (method.getParameters().isEmpty() && isTypeName(method.getType().asString(), "ProjectBuildingRequest")) {
                    candidates.add(new MethodCallExpr(new ThisExpr(), method.getNameAsString()));
                }
            }
        });

        call.findAncestor(MethodDeclaration.class).ifPresent(method -> {
            for (Parameter parameter : method.getParameters()) {
                if (isTypeName(parameter.getType().asString(), "ProjectBuildingRequest")) {
                    candidates.add(new NameExpr(parameter.getNameAsString()));
                }
            }

            method.findAll(VariableDeclarationExpr.class).stream()
                    .filter(v -> isBeforeOrEqual(v, call))
                    .flatMap(v -> v.getVariables().stream())
                    .filter(v -> isTypeName(v.getType().asString(), "ProjectBuildingRequest"))
                    .map(v -> new NameExpr(v.getNameAsString()))
                    .forEach(candidates::add);
        });

        call.findAncestor(com.github.javaparser.ast.expr.LambdaExpr.class).ifPresent(lambda -> {
            for (Parameter parameter : lambda.getParameters()) {
                if (isTypeName(parameter.getType().asString(), "ProjectBuildingRequest")) {
                    candidates.add(new NameExpr(parameter.getNameAsString()));
                }
            }

            if (lambda.getBody() instanceof BlockStmt) {
                BlockStmt block = lambda.getBody().asBlockStmt();
                block.findAll(VariableDeclarationExpr.class).stream()
                        .filter(v -> isBeforeOrEqual(v, call))
                        .flatMap(v -> v.getVariables().stream())
                        .filter(v -> isTypeName(v.getType().asString(), "ProjectBuildingRequest"))
                        .map(v -> new NameExpr(v.getNameAsString()))
                        .forEach(candidates::add);
            }
        });

        return candidates.stream()
                .sorted(Comparator.comparingInt(Main::expressionPriority))
                .findFirst();
    }

    private static boolean isBeforeOrEqual(com.github.javaparser.ast.Node left, com.github.javaparser.ast.Node right) {
        return left.getRange().flatMap(lr -> right.getRange().map(rr -> lr.begin.isBefore(rr.begin) || lr.begin.equals(rr.begin))).orElse(false);
    }

    private static int expressionPriority(Expression expression) {
        if (expression instanceof NameExpr) {
            return 0;
        }
        if (expression instanceof FieldAccessExpr) {
            return 1;
        }
        if (expression instanceof MethodCallExpr) {
            return 2;
        }
        return 3;
    }

    private static boolean isTypeName(String actualType, String expectedSimpleName) {
        return actualType != null && (actualType.equals(expectedSimpleName) || actualType.endsWith("." + expectedSimpleName));
    }
}
