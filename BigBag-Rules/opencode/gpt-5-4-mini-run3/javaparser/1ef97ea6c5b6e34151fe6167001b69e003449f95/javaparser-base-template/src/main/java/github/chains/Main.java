package github.chains;

import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.type.Type;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class Main {

    private static final String LEGACY_DATE_TYPE = "java.util.Date";
    private static final String NEW_TIMESTAMP_TYPE = "java.sql.Timestamp";
    private static final String METHOD_NAME = "between";

    public static void main(String[] args) throws IOException {
        Path sourceRoot = Paths.get(args.length > 0 ? args[0] : ".").toAbsolutePath().normalize();
        if (!Files.exists(sourceRoot)) {
            throw new IllegalArgumentException("Source root does not exist: " + sourceRoot);
        }

        StaticJavaParser.getParserConfiguration().setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_8);

        List<Path> javaFiles = new ArrayList<>();
        try (java.util.stream.Stream<Path> stream = Files.walk(sourceRoot)) {
            stream.filter(path -> path.toString().endsWith(".java")).forEach(javaFiles::add);
        }

        for (Path file : javaFiles) {
            CompilationUnit cu = StaticJavaParser.parse(file);
            boolean changed = false;
            changed |= rewriteLegacyBetweenDeclarations(cu);
            changed |= rewriteLegacyBetweenCalls(cu);
            if (changed) {
                Files.write(file, cu.toString().getBytes(StandardCharsets.UTF_8));
            }
        }
    }

    private static boolean rewriteLegacyBetweenDeclarations(CompilationUnit cu) {
        boolean changed = false;
        for (MethodDeclaration method : cu.findAll(MethodDeclaration.class)) {
            if (!METHOD_NAME.equals(method.getNameAsString()) || method.getParameters().size() != 2) {
                continue;
            }
            if (!isDateType(method.getParameter(0).getType()) || !isDateType(method.getParameter(1).getType())) {
                continue;
            }

            method.setType(StaticJavaParser.parseType(NEW_TIMESTAMP_TYPE));
            method.getParameter(0).setType(StaticJavaParser.parseType(NEW_TIMESTAMP_TYPE));
            method.getParameter(1).setType(StaticJavaParser.parseType(NEW_TIMESTAMP_TYPE));
            changed = true;
        }
        return changed;
    }

    private static boolean rewriteLegacyBetweenCalls(CompilationUnit cu) {
        boolean changed = false;
        for (MethodCallExpr call : cu.findAll(MethodCallExpr.class)) {
            if (!METHOD_NAME.equals(call.getNameAsString()) || call.getArguments().size() != 2) {
                continue;
            }
            if (call.getArguments().stream().allMatch(Main::isDateLikeExpression)) {
                for (int i = 0; i < call.getArguments().size(); i++) {
                    Expression arg = call.getArgument(i);
                    if (!isTimestampExpression(arg)) {
                        call.setArgument(i, wrapWithTimestamp(arg));
                        changed = true;
                    }
                }
            }
        }
        return changed;
    }

    private static boolean isLikelyDateExpression(Expression expression) {
        if (expression.isObjectCreationExpr()) {
            ObjectCreationExpr creation = expression.asObjectCreationExpr();
            return isDateType(creation.getType());
        }
        if (expression.isCastExpr()) {
            return isDateType(expression.asCastExpr().getType());
        }
        return false;
    }

    private static boolean isDateType(Type type) {
        return isDateTypeName(type.toString());
    }

    private static boolean isDateTypeName(String typeName) {
        return LEGACY_DATE_TYPE.equals(typeName) || "Date".equals(typeName) || typeName.endsWith(".Date");
    }

    private static boolean isTimestampExpression(Expression expression) {
        if (expression.isObjectCreationExpr()) {
            return NEW_TIMESTAMP_TYPE.equals(expression.asObjectCreationExpr().getType().toString())
                    || "Timestamp".equals(expression.asObjectCreationExpr().getType().toString());
        }
        return false;
    }

    private static boolean isDateLikeExpression(Expression expression) {
        if (isLikelyDateExpression(expression)) {
            return true;
        }
        return expression.isNameExpr() || expression.isMethodCallExpr() || expression.isFieldAccessExpr();
    }

    private static Expression wrapWithTimestamp(Expression expression) {
        MethodCallExpr getTime = new MethodCallExpr(expression.clone(), "getTime");
        return new ObjectCreationExpr(
                null,
                StaticJavaParser.parseClassOrInterfaceType(NEW_TIMESTAMP_TYPE),
                com.github.javaparser.ast.NodeList.nodeList(getTime));
    }
}
