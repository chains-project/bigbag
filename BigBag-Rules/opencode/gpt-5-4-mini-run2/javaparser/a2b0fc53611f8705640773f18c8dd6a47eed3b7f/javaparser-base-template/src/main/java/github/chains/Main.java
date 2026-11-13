package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
import com.github.javaparser.ast.nodeTypes.NodeWithStatements;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.Statement;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class Main {
    public static void main(String[] args) {
        final JavaParser parser = new JavaParser(new ParserConfiguration().setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_11));

        final Path[] sourceRoots = args.length == 0 ? new Path[] {Paths.get(".")} : java.util.Arrays.stream(args).map(Paths::get).toArray(Path[]::new);
        for (final Path sourceRoot : sourceRoots) {
            try {
                Files.walk(sourceRoot)
                        .filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                        .forEach(path -> transformFile(parser, path));
            } catch (IOException e) {
                throw new RuntimeException("Failed to walk source tree: " + sourceRoot, e);
            }
        }
    }

    private static void transformFile(final JavaParser parser, final Path path) {
        try {
            final CompilationUnit compilationUnit = parser.parse(path).getResult()
                    .orElseThrow(() -> new IllegalStateException("Unable to parse " + path));

            if (transformCompilationUnit(compilationUnit)) {
                Files.write(path, compilationUnit.toString().getBytes(StandardCharsets.UTF_8));
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to transform " + path, e);
        }
    }

    private static boolean transformCompilationUnit(final CompilationUnit compilationUnit) {
        boolean modified = false;
        for (final BlockStmt block : compilationUnit.findAll(BlockStmt.class)) {
            modified |= transformBlock(block);
        }
        return modified;
    }

    private static boolean transformBlock(final BlockStmt block) {
        boolean modified = false;
        for (int i = 0; i < block.getStatements().size(); i++) {
            final Statement statement = block.getStatement(i);
            if (!(statement.isExpressionStmt() || statement.isBlockStmt() || statement.isIfStmt() || statement.isForStmt()
                    || statement.isWhileStmt() || statement.isDoStmt() || statement.isTryStmt() || statement.isSwitchStmt()
                    || statement.isSynchronizedStmt() || statement.isReturnStmt() || statement.isThrowStmt()
                    || statement.isLabeledStmt() || statement.isLocalClassDeclarationStmt() || statement.isExplicitConstructorInvocationStmt()
                    || statement.isEmptyStmt())) {
                continue;
            }

            if (!statement.isExpressionStmt()) {
                continue;
            }

            final Expression expression = statement.asExpressionStmt().getExpression();
            if (!expression.isVariableDeclarationExpr()) {
                continue;
            }

            final VariableDeclarationExpr declarationExpr = expression.asVariableDeclarationExpr();
            if (declarationExpr.getVariables().size() != 1) {
                continue;
            }

            final VariableDeclarator variable = declarationExpr.getVariable(0);
            if (!isFlywayType(variable.getType().asString())) {
                continue;
            }

            if (variable.getInitializer().isEmpty() || !variable.getInitializer().get().isObjectCreationExpr()) {
                continue;
            }

            final ObjectCreationExpr creationExpr = variable.getInitializer().get().asObjectCreationExpr();
            if (!isFlywayCreation(creationExpr) || !creationExpr.getArguments().isEmpty()) {
                continue;
            }

            final List<Statement> statementsToRemove = new ArrayList<>();
            final List<MethodCallExpr> setterCalls = new ArrayList<>();
            Expression classLoaderArgument = null;

            int j = i + 1;
            while (j < block.getStatements().size()) {
                final Statement nextStatement = block.getStatement(j);
                if (!nextStatement.isExpressionStmt()) {
                    break;
                }

                final Expression nextExpression = nextStatement.asExpressionStmt().getExpression();
                if (!nextExpression.isMethodCallExpr()) {
                    break;
                }

                final MethodCallExpr methodCall = nextExpression.asMethodCallExpr();
                if (!isSetterCallOnVariable(methodCall, variable.getNameAsString())) {
                    break;
                }

                statementsToRemove.add(nextStatement);
                if ("setClassLoader".equals(methodCall.getNameAsString()) && methodCall.getArguments().size() == 1) {
                    classLoaderArgument = methodCall.getArgument(0).clone();
                } else {
                    setterCalls.add(methodCall);
                }
                j++;
            }

            final MethodCallExpr replacement = buildFlywayExpression(classLoaderArgument, setterCalls);
            variable.setInitializer(replacement);
            for (final Statement toRemove : statementsToRemove) {
                block.getStatements().remove(toRemove);
            }

            modified = true;
        }
        return modified;
    }

    private static MethodCallExpr buildFlywayExpression(final Expression classLoaderArgument,
                                                        final List<MethodCallExpr> setterCalls) {
        final MethodCallExpr configureCall = classLoaderArgument == null
                ? new MethodCallExpr(new NameExpr("Flyway"), "configure")
                : new MethodCallExpr(new NameExpr("Flyway"), "configure", NodeList.nodeList(classLoaderArgument));

        MethodCallExpr chain = configureCall;
        for (final MethodCallExpr setterCall : setterCalls) {
            chain = new MethodCallExpr(chain, toFluentMethodName(setterCall.getNameAsString()), cloneArguments(setterCall));
        }
        return new MethodCallExpr(chain, "load");
    }

    private static NodeList<Expression> cloneArguments(final MethodCallExpr methodCall) {
        final NodeList<Expression> arguments = new NodeList<>();
        for (final Expression argument : methodCall.getArguments()) {
            arguments.add(argument.clone());
        }
        return arguments;
    }

    private static String toFluentMethodName(final String setterName) {
        if (setterName.startsWith("set") && setterName.length() > 3) {
            final String suffix = setterName.substring(3);
            return Character.toLowerCase(suffix.charAt(0)) + suffix.substring(1);
        }
        return setterName;
    }

    private static boolean isFlywayCreation(final ObjectCreationExpr creationExpr) {
        return isFlywayType(creationExpr.getType().asString()) && creationExpr.getTypeArguments().isEmpty();
    }

    private static boolean isFlywayType(final String typeName) {
        return "Flyway".equals(typeName) || "org.flywaydb.core.Flyway".equals(typeName);
    }

    private static boolean isSetterCallOnVariable(final MethodCallExpr methodCall, final String variableName) {
        return methodCall.getScope().map(scope -> scope.isNameExpr() && variableName.equals(scope.asNameExpr().getNameAsString()))
                .orElse(false) && methodCall.getNameAsString().startsWith("set");
    }
}
