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
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class Main {
    private static final String OLD_METHOD = "buildDependencyGraph";
    private static final String OLD_TYPE = "org.apache.maven.project.MavenProject";
    private static final String NEW_TYPE = "org.apache.maven.project.ProjectBuildingRequest";

    public static void main(String[] args) throws IOException {
        if (args.length == 0) {
            throw new IllegalArgumentException("Usage: Main <source-root>");
        }

        Path sourceRoot = Paths.get(args[0]).toAbsolutePath().normalize();
        if (!Files.isDirectory(sourceRoot)) {
            throw new IllegalArgumentException("Not a directory: " + sourceRoot);
        }

        ParserConfiguration configuration = new ParserConfiguration();
        JavaParser parser = new JavaParser(configuration);
        List<Path> javaFiles;
        try (var stream = Files.walk(sourceRoot)) {
            javaFiles = stream.filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                .sorted()
                .collect(Collectors.toList());
        }

        for (Path file : javaFiles) {
            Optional<CompilationUnit> parsed = parser.parse(file).getResult();
            if (parsed.isEmpty()) {
                continue;
            }

            CompilationUnit cu = parsed.get();
            LexicalPreservingPrinter.setup(cu);
            boolean changed = transformCompilationUnit(cu);
            if (changed) {
                Files.writeString(file, LexicalPreservingPrinter.print(cu));
            }
        }
    }

    private static boolean transformCompilationUnit(CompilationUnit cu) {
        boolean changed = false;
        for (MethodCallExpr call : cu.findAll(MethodCallExpr.class)) {
            if (!isTargetCall(call)) {
                continue;
            }

            Optional<Expression> replacement = findReplacementExpression(call);
            if (replacement.isPresent() && !replacement.get().toString().equals(call.getArgument(0).toString())) {
                call.setArgument(0, replacement.get().clone());
                changed = true;
            }
        }
        return changed;
    }

    private static boolean isTargetCall(MethodCallExpr call) {
        return OLD_METHOD.equals(call.getNameAsString()) && call.getArguments().size() == 2 && call.getArgument(0).isNameExpr();
    }

    private static boolean typeMatches(String declaredType, String fqcn) {
        String simple = fqcn.substring(fqcn.lastIndexOf('.') + 1);
        return declaredType.equals(fqcn) || declaredType.equals(simple) || declaredType.endsWith("." + simple)
            || Pattern.compile("\\b" + Pattern.quote(simple) + "\\b").matcher(declaredType).find();
    }

    private static Optional<Expression> findReplacementExpression(MethodCallExpr call) {
        List<Expression> candidates = new ArrayList<>();

        call.findAncestor(MethodDeclaration.class).ifPresent(method ->
            method.getParameters().stream()
                .filter(parameter -> typeMatches(parameter.getType().asString(), NEW_TYPE))
                .map(parameter -> (Expression) new NameExpr(parameter.getNameAsString()))
                .forEach(candidates::add)
        );

        call.findAncestor(ConstructorDeclaration.class).ifPresent(constructor ->
            constructor.getParameters().stream()
                .filter(parameter -> typeMatches(parameter.getType().asString(), NEW_TYPE))
                .map(parameter -> (Expression) new NameExpr(parameter.getNameAsString()))
                .forEach(candidates::add)
        );

        call.findAncestor(ClassOrInterfaceDeclaration.class).ifPresent(type ->
            type.getFields().stream()
                .filter(field -> typeMatches(field.getElementType().asString(), NEW_TYPE))
                .flatMap(field -> field.getVariables().stream())
                .map(variable -> (Expression) new NameExpr(variable.getNameAsString()))
                .forEach(candidates::add)
        );

        return candidates.stream()
            .sorted((left, right) -> {
                int byScore = Integer.compare(replacementScore(left), replacementScore(right));
                if (byScore != 0) {
                    return byScore;
                }
                return left.toString().compareTo(right.toString());
            })
            .findFirst();
    }

    private static int replacementScore(Expression expression) {
        String name = expression.toString();
        if (name.equals("buildingRequest") || name.endsWith("Request") || name.startsWith("get") && name.endsWith("Request")) {
            return 0;
        }
        return 1;
    }
}
