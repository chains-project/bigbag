package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

public class Main {

    private static final String FLYWAY_FQCN = "org.flywaydb.core.Flyway";

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            throw new IllegalArgumentException("Expected a single source root path");
        }

        final Path sourceRoot = Paths.get(args[0]);
        try (Stream<Path> paths = Files.walk(sourceRoot)) {
            paths.filter(path -> path.toString().endsWith(".java")).forEach(Main::transformFile);
        }
    }

    private static void transformFile(Path file) {
        try {
            final CompilationUnit cu = StaticJavaParser.parse(file);
            LexicalPreservingPrinter.setup(cu);

            if (transformCompilationUnit(cu)) {
                Files.writeString(file, LexicalPreservingPrinter.print(cu), StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to transform " + file, e);
        }
    }

    private static boolean transformCompilationUnit(CompilationUnit cu) {
        boolean changed = false;
        for (BlockStmt block : cu.findAll(BlockStmt.class)) {
            changed |= transformBlock(block);
        }
        return changed;
    }

    private static boolean transformBlock(BlockStmt block) {
        boolean changed = false;
        final NodeList<Statement> statements = block.getStatements();

        for (int i = 0; i < statements.size(); i++) {
            final Statement statement = statements.get(i);
            if (!statement.isExpressionStmt()) {
                continue;
            }

            final Expression expression = statement.asExpressionStmt().getExpression();
            if (!expression.isVariableDeclarationExpr()) {
                continue;
            }

            final VariableDeclarationExpr declaration = expression.asVariableDeclarationExpr();
            if (declaration.getVariables().size() != 1) {
                continue;
            }

            final VariableDeclarator variable = declaration.getVariable(0);
            if (!isFlywayType(variable.getType().asString())) {
                continue;
            }

            if (variable.getInitializer().isEmpty() || !isEmptyFlywayCreation(variable.getInitializer().get())) {
                continue;
            }

            final String variableName = variable.getNameAsString();
            final List<Integer> removeIndices = new ArrayList<>();
            final List<MethodCallExpr> setters = new ArrayList<>();

            for (int j = i + 1; j < statements.size(); j++) {
                final Statement next = statements.get(j);
                if (!next.isExpressionStmt()) {
                    break;
                }

                final Expression nextExpression = next.asExpressionStmt().getExpression();
                if (!nextExpression.isMethodCallExpr()) {
                    break;
                }

                final MethodCallExpr methodCall = nextExpression.asMethodCallExpr();
                if (!isCallOnVariable(methodCall, variableName)) {
                    break;
                }

                if (!isSupportedSetter(methodCall)) {
                    break;
                }

                setters.add(methodCall);
                removeIndices.add(j);
            }

            if (setters.isEmpty()) {
                continue;
            }

            variable.setInitializer(buildReplacementExpression(setters));
            Collections.reverse(removeIndices);
            for (int index : removeIndices) {
                statements.remove(index);
            }
            changed = true;
        }

        return changed;
    }

    private static boolean isFlywayType(String typeName) {
        return FLYWAY_FQCN.equals(typeName) || "Flyway".equals(typeName) || typeName.endsWith(".Flyway");
    }

    private static boolean isEmptyFlywayCreation(Expression expression) {
        if (!expression.isObjectCreationExpr()) {
            return false;
        }

        final ObjectCreationExpr creation = expression.asObjectCreationExpr();
        return isFlywayType(creation.getType().asString()) && creation.getArguments().isEmpty();
    }

    private static boolean isCallOnVariable(MethodCallExpr call, String variableName) {
        final Optional<Expression> scope = call.getScope();
        return scope.isPresent() && scope.get().isNameExpr() && scope.get().asNameExpr().getNameAsString().equals(variableName);
    }

    private static boolean isSupportedSetter(MethodCallExpr call) {
        switch (call.getNameAsString()) {
            case "setDataSource":
            case "setLocations":
            case "setValidateOnMigrate":
            case "setClassLoader":
                return true;
            default:
                return false;
        }
    }

    private static Expression buildReplacementExpression(List<MethodCallExpr> setters) {
        final List<String> chainParts = new ArrayList<>();
        Expression classLoaderArgument = null;

        for (MethodCallExpr setter : setters) {
            switch (setter.getNameAsString()) {
                case "setClassLoader":
                    if (!setter.getArguments().isEmpty()) {
                        classLoaderArgument = setter.getArgument(0).clone();
                    }
                    break;
                case "setDataSource":
                    chainParts.add(buildCallPart("dataSource", setter.getArguments()));
                    break;
                case "setLocations":
                    chainParts.add(buildCallPart("locations", setter.getArguments()));
                    break;
                case "setValidateOnMigrate":
                    chainParts.add(buildCallPart("validateOnMigrate", setter.getArguments()));
                    break;
                default:
                    break;
            }
        }

        final StringBuilder builder = new StringBuilder();
        builder.append(FLYWAY_FQCN).append(".configure(");
        if (classLoaderArgument != null) {
            builder.append(classLoaderArgument);
        }
        builder.append(")");
        for (String part : chainParts) {
            builder.append(part);
        }
        builder.append(".load()");
        return StaticJavaParser.parseExpression(builder.toString());
    }

    private static String buildCallPart(String methodName, NodeList<Expression> arguments) {
        final StringBuilder builder = new StringBuilder();
        builder.append('.').append(methodName).append('(');
        for (int i = 0; i < arguments.size(); i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append(arguments.get(i));
        }
        builder.append(')');
        return builder.toString();
    }
}
