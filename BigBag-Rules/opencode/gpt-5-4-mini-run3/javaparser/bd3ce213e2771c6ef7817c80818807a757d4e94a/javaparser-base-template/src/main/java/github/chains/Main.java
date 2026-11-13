package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;
import com.github.javaparser.StaticJavaParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Main {
    public static void main(String[] args) {
        if (args.length == 0) {
            System.err.println("Usage: Main <source-root>");
            System.exit(1);
        }

        Path sourceRoot = Paths.get(args[0]).toAbsolutePath().normalize();
        if (!Files.isDirectory(sourceRoot)) {
            System.err.println("Source root does not exist or is not a directory: " + sourceRoot);
            System.exit(1);
        }

        try {
            JavaParser parser = new JavaParser(new ParserConfiguration());
            Files.walk(sourceRoot)
                    .filter(path -> path.toString().endsWith(".java"))
                    .forEach(path -> transformFile(parser, path));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void transformFile(JavaParser parser, Path javaFile) {
        try {
            CompilationUnit cu = parser.parse(javaFile).getResult().orElse(null);
            if (cu == null) {
                return;
            }

            LexicalPreservingPrinter.setup(cu);
            Map<String, String> knownTypes = collectKnownTypes(cu);
            boolean changed = rewriteJacksonFileWrites(cu, knownTypes);
            if (changed) {
                Files.writeString(javaFile, LexicalPreservingPrinter.print(cu), StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to transform " + javaFile, e);
        }
    }

    private static Map<String, String> collectKnownTypes(CompilationUnit cu) {
        Map<String, String> knownTypes = new HashMap<>();
        cu.findAll(FieldDeclaration.class).forEach(field -> field.getVariables()
                .forEach(variable -> knownTypes.put(variable.getNameAsString(), variable.getType().asString())));
        cu.findAll(Parameter.class).forEach(parameter ->
                knownTypes.put(parameter.getNameAsString(), parameter.getType().asString()));
        cu.findAll(VariableDeclarator.class).forEach(variable ->
                knownTypes.put(variable.getNameAsString(), variable.getType().asString()));
        return knownTypes;
    }

    private static boolean rewriteJacksonFileWrites(CompilationUnit cu, Map<String, String> knownTypes) {
        boolean changed = false;
        List<MethodCallExpr> calls = new ArrayList<>(cu.findAll(MethodCallExpr.class));
        for (MethodCallExpr call : calls) {
            if (!isTargetJacksonWriteValue(call, knownTypes)) {
                continue;
            }

            Statement stmt = call.findAncestor(Statement.class).orElse(null);
            if (stmt == null) {
                continue;
            }

            Expression targetFile = call.getArgument(0).clone();
            Expression payload = call.getArgument(1).clone();
            Expression writer = call.getScope().orElseThrow(IllegalStateException::new).clone();

            Statement replacement = StaticJavaParser.parseStatement(
                    String.format(
                            "java.nio.file.Files.write(%s.toPath(), %s.writeValueAsString(%s).getBytes(java.nio.charset.StandardCharsets.UTF_8));",
                            targetFile,
                            writer,
                            payload));
            stmt.replace(replacement);
            changed = true;
        }
        return changed;
    }

    private static boolean isTargetJacksonWriteValue(MethodCallExpr call, Map<String, String> knownTypes) {
        if (!"writeValue".equals(call.getNameAsString()) || call.getArguments().size() != 2 || call.getScope().isEmpty()) {
            return false;
        }

        Expression scope = call.getScope().get();
        return isJacksonWriterScope(scope, knownTypes) && isFileLikeExpression(call.getArgument(0), knownTypes);
    }

    private static boolean isJacksonWriterScope(Expression scope, Map<String, String> knownTypes) {
        if (isTypeNamed(scope, knownTypes, "com.fasterxml.jackson.databind.ObjectWriter")
                || isTypeNamed(scope, knownTypes, "com.fasterxml.jackson.databind.ObjectMapper")) {
            return true;
        }

        if (scope instanceof MethodCallExpr) {
            MethodCallExpr methodCall = scope.asMethodCallExpr();
            if (!methodCall.getNameAsString().startsWith("writer")) {
                return false;
            }
            return methodCall.getScope().isPresent()
                    && isJacksonObjectMapperScope(methodCall.getScope().get(), knownTypes);
        }

        return false;
    }

    private static boolean isJacksonObjectMapperScope(Expression scope, Map<String, String> knownTypes) {
        return isTypeNamed(scope, knownTypes, "com.fasterxml.jackson.databind.ObjectMapper");
    }

    private static boolean isFileLikeExpression(Expression expression, Map<String, String> knownTypes) {
        return isTypeNamed(expression, knownTypes, "java.io.File") || isTypeNamed(expression, knownTypes, "File");
    }

    private static boolean isTypeNamed(Expression expression, Map<String, String> knownTypes, String expectedType) {
        String type = resolveKnownType(expression, knownTypes);
        if (type == null) {
            return false;
        }
        String simpleExpectedType = expectedType.contains(".")
                ? expectedType.substring(expectedType.lastIndexOf('.') + 1)
                : expectedType;
        return type.equals(expectedType)
                || type.equals(simpleExpectedType)
                || type.endsWith("." + simpleExpectedType)
                || type.endsWith(expectedType)
                || type.endsWith(simpleExpectedType);
    }

    private static String resolveKnownType(Expression expression, Map<String, String> knownTypes) {
        if (expression instanceof NameExpr) {
            return knownTypes.get(expression.asNameExpr().getNameAsString());
        }
        if (expression instanceof FieldAccessExpr) {
            return knownTypes.get(expression.asFieldAccessExpr().getNameAsString());
        }
        if (expression instanceof ObjectCreationExpr) {
            return expression.asObjectCreationExpr().getType().asString();
        }
        return null;
    }
}
