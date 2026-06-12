package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

public class Main {
    private static final String TARGET_METHOD = "enableLogging";
    private static final String TARGET_TYPE_PREFIX = "org.codehaus.plexus.archiver.";

    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            throw new IllegalArgumentException("Usage: Main <source-root> [output-root]");
        }

        Path sourceRoot = Path.of(args[0]).toAbsolutePath().normalize();
        Path outputRoot = args.length > 1 ? Path.of(args[1]).toAbsolutePath().normalize() : sourceRoot;

        JavaParser parser = new JavaParser(new ParserConfiguration());

        try (var paths = Files.walk(sourceRoot)) {
            List<Path> javaFiles = paths
                .filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                .toList();

            for (Path file : javaFiles) {
                CompilationUnit cu = parser.parse(file).getResult().orElseThrow(() ->
                    new IllegalStateException("Failed to parse " + file));

                LexicalPreservingPrinter.setup(cu);

                boolean changed = false;
                for (MethodCallExpr call : cu.findAll(MethodCallExpr.class)) {
                    if (!isTargetEnableLoggingCall(call)) {
                        continue;
                    }

                    Optional<ExpressionStmt> stmt = call.findAncestor(ExpressionStmt.class);
                    if (stmt.isPresent()) {
                        stmt.get().remove();
                        changed = true;
                    }
                }

                if (changed) {
                    Path relative = sourceRoot.relativize(file);
                    Path destination = outputRoot.resolve(relative);
                    Files.createDirectories(destination.getParent());
                    Files.writeString(destination, LexicalPreservingPrinter.print(cu), StandardCharsets.UTF_8);
                } else if (!sourceRoot.equals(outputRoot)) {
                    Path relative = sourceRoot.relativize(file);
                    Path destination = outputRoot.resolve(relative);
                    Files.createDirectories(destination.getParent());
                    Files.copy(file, destination, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    private static boolean isTargetEnableLoggingCall(MethodCallExpr call) {
        if (!TARGET_METHOD.equals(call.getNameAsString()) || call.getArguments().size() != 1) {
            return false;
        }

        Optional<Expression> scope = call.getScope();
        if (scope.isEmpty()) {
            return false;
        }

        Expression receiver = scope.get();
        return looksLikeUnArchiverType(receiver, call.findCompilationUnit().orElse(null));
    }

    private static boolean looksLikeUnArchiverType(Expression expression, CompilationUnit cu) {
        if (expression == null) {
            return false;
        }
        if (expression instanceof ObjectCreationExpr) {
            ObjectCreationExpr creation = (ObjectCreationExpr) expression;
            return endsWithUnArchiver(creation.getType().asString());
        }
        if (expression instanceof FieldAccessExpr) {
            FieldAccessExpr access = (FieldAccessExpr) expression;
            return isDeclaredAsUnArchiver(access.getNameAsString(), cu);
        }
        if (expression instanceof NameExpr) {
            NameExpr name = (NameExpr) expression;
            return isDeclaredAsUnArchiver(name.getNameAsString(), cu);
        }
        return endsWithUnArchiver(expression.toString());
    }

    private static boolean isDeclaredAsUnArchiver(String name, CompilationUnit cu) {
        if (cu == null) {
            return false;
        }

        for (VariableDeclarator variable : cu.findAll(VariableDeclarator.class)) {
            if (name.equals(variable.getNameAsString()) && endsWithUnArchiver(variable.getType().asString())) {
                return true;
            }
        }
        for (FieldDeclaration field : cu.findAll(FieldDeclaration.class)) {
            for (VariableDeclarator variable : field.getVariables()) {
                if (name.equals(variable.getNameAsString()) && endsWithUnArchiver(variable.getType().asString())) {
                    return true;
                }
            }
        }
        for (Parameter parameter : cu.findAll(Parameter.class)) {
            if (name.equals(parameter.getNameAsString()) && endsWithUnArchiver(parameter.getType().asString())) {
                return true;
            }
        }
        return false;
    }

    private static boolean endsWithUnArchiver(String typeName) {
        return "AbstractUnArchiver".equals(typeName)
            || typeName.endsWith("UnArchiver")
            || typeName.startsWith(TARGET_TYPE_PREFIX) && typeName.contains("UnArchiver");
    }
}
