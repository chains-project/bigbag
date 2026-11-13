package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.expr.EnclosedExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.CastExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Stream;

public class Main {
    public static void main(String[] args) {
        Path root = Paths.get(args.length > 0 ? args[0] : ".");
        try {
            transform(root);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void transform(Path root) throws IOException {
        try (Stream<Path> paths = Files.walk(root)) {
            paths.filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                    .forEach(
                            path -> {
                                try {
                                    CompilationUnit cu = StaticJavaParser.parse(path);
                                    boolean changed = false;
                                    changed |= rewriteBetweenDeclarations(cu);
                                    changed |= rewriteBetweenCalls(cu);
                                    if (changed) {
                                        Files.writeString(path, cu.toString(), StandardCharsets.UTF_8);
                                    }
                                } catch (IOException e) {
                                    throw new RuntimeException(e);
                                }
                            });
        }
    }

    private static boolean rewriteBetweenDeclarations(CompilationUnit cu) {
        boolean changed = false;
        for (MethodDeclaration method : cu.findAll(MethodDeclaration.class)) {
            if (!method.getNameAsString().equals("between")) {
                continue;
            }
            List<Parameter> parameters = method.getParameters();
            if (parameters.size() < 2 || parameters.size() > 3) {
                continue;
            }

            boolean signatureMatches = isDateType(parameters.get(0)) && isDateType(parameters.get(1));
            if (!signatureMatches) {
                continue;
            }

            parameters.get(0).setType("java.sql.Timestamp");
            parameters.get(1).setType("java.sql.Timestamp");
            if (isDateType(method.getType())) {
                method.setType("java.sql.Timestamp");
            }
            changed = true;
        }
        return changed;
    }

    private static boolean rewriteBetweenCalls(CompilationUnit cu) {
        boolean changed = false;
        for (MethodCallExpr call : cu.findAll(MethodCallExpr.class)) {
            if (!call.getNameAsString().equals("between")) {
                continue;
            }
            if (call.getArguments().size() < 2 || call.getArguments().size() > 3) {
                continue;
            }
            if (!looksLikeDateAndTimeCall(call)) {
                continue;
            }

            call.setArgument(0, wrapAsTimestamp(call.getArgument(0)));
            call.setArgument(1, wrapAsTimestamp(call.getArgument(1)));
            changed = true;
        }
        return changed;
    }

    private static boolean looksLikeDateAndTimeCall(MethodCallExpr call) {
        if (call.getScope().isPresent()) {
            Expression scope = call.getScope().get();
            if (scope instanceof MethodCallExpr) {
                return ((MethodCallExpr) scope).getNameAsString().equals("date");
            }
            if (scope.toString().endsWith(".date()") || scope.toString().equals("date()")) {
                return true;
            }
        }

        return call.findAncestor(ClassOrInterfaceDeclaration.class)
                .map(Main::extendsDateAndTime)
                .orElse(false);
    }

    private static boolean extendsDateAndTime(ClassOrInterfaceDeclaration declaration) {
        return declaration.getExtendedTypes().stream()
                .map(ClassOrInterfaceType::getNameAsString)
                .anyMatch(name -> name.equals("DateAndTime") || name.endsWith(".DateAndTime"));
    }

    private static boolean isDateType(Parameter parameter) {
        return isDateType(parameter.getType().asString());
    }

    private static boolean isDateType(com.github.javaparser.ast.type.Type type) {
        return isDateType(type.asString());
    }

    private static boolean isDateType(String typeName) {
        return typeName.equals("Date")
                || typeName.equals("java.util.Date")
                || typeName.endsWith(".Date");
    }

    private static Expression wrapAsTimestamp(Expression expression) {
        if (isTimestampExpression(expression)) {
            return expression;
        }
        return StaticJavaParser.parseExpression(
                "new java.sql.Timestamp(" + expression + ".getTime())");
    }

    private static boolean isTimestampExpression(Expression expression) {
        if (expression instanceof ObjectCreationExpr) {
            ObjectCreationExpr creation = (ObjectCreationExpr) expression;
            return creation.getType().asString().equals("Timestamp")
                    || creation.getType().asString().equals("java.sql.Timestamp");
        }
        if (expression instanceof CastExpr) {
            CastExpr castExpr = (CastExpr) expression;
            return castExpr.getType().asString().equals("java.sql.Timestamp")
                    || castExpr.getType().asString().equals("Timestamp");
        }
        if (expression instanceof EnclosedExpr) {
            return isTimestampExpression(((EnclosedExpr) expression).getInner());
        }
        if (expression instanceof NameExpr || expression instanceof FieldAccessExpr) {
            return false;
        }
        return expression.toString().contains("new java.sql.Timestamp(")
                || expression.toString().contains("new Timestamp(");
    }
}
