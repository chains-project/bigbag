package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class Main {
    private static final String FLYWAY_FQN = "org.flywaydb.core.Flyway";
    private static final String FLYWAY_SIMPLE = "Flyway";

    public static void main(String[] args) throws Exception {
        final Path sourceRoot = args.length == 0 ? Paths.get(".") : Paths.get(args[0]);
        Files.walk(sourceRoot)
            .filter(path -> path.toString().endsWith(".java"))
            .forEach(Main::transformFile);
    }

    private static void transformFile(Path path) {
        try {
            final CompilationUnit cu = StaticJavaParser.parse(path, StandardCharsets.UTF_8);
            LexicalPreservingPrinter.setup(cu);
            if (transformCompilationUnit(cu)) {
                Files.writeString(path, LexicalPreservingPrinter.print(cu), StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to transform " + path, e);
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
        for (int i = 0; i < block.getStatements().size(); i++) {
            final Statement statement = block.getStatement(i);
            final Optional<VariableDeclarationExpr> declarationOpt = statement.isExpressionStmt()
                ? statement.asExpressionStmt().getExpression().toVariableDeclarationExpr()
                : Optional.empty();
            if (!declarationOpt.isPresent()) {
                continue;
            }

            final VariableDeclarationExpr declaration = declarationOpt.get();
            if (declaration.getVariables().size() != 1) {
                continue;
            }

            if (!isFlywayType(declaration.getCommonType())) {
                continue;
            }

            final String variableName = declaration.getVariable(0).getNameAsString();
            final Optional<Expression> initializerOpt = declaration.getVariable(0).getInitializer();
            if (!initializerOpt.isPresent() || !isDeprecatedFlywayConstruction(initializerOpt.get())) {
                continue;
            }

            final List<Integer> removedIndices = new ArrayList<>();
            final List<MethodCallExpr> setterCalls = new ArrayList<>();
            Expression classLoaderExpression = null;

            for (int j = i + 1; j < block.getStatements().size(); j++) {
                final Statement candidate = block.getStatement(j);
                if (!candidate.isExpressionStmt()) {
                    break;
                }

                final Expression expression = candidate.asExpressionStmt().getExpression();
                if (!expression.isMethodCallExpr()) {
                    break;
                }

                final MethodCallExpr call = expression.asMethodCallExpr();
                if (!isCallOnVariable(call, variableName)) {
                    break;
                }

                switch (call.getNameAsString()) {
                    case "setClassLoader":
                        if (call.getArguments().size() == 1 && classLoaderExpression == null) {
                            classLoaderExpression = call.getArgument(0).clone();
                            removedIndices.add(j);
                        } else {
                            j = block.getStatements().size();
                        }
                        break;
                    case "setDataSource":
                    case "setLocations":
                    case "setValidateOnMigrate":
                        setterCalls.add(call.clone());
                        removedIndices.add(j);
                        break;
                    default:
                        j = block.getStatements().size();
                        break;
                }

                if (j >= block.getStatements().size()) {
                    break;
                }
            }

            if (setterCalls.isEmpty() && classLoaderExpression == null) {
                continue;
            }

            final Expression replacement = buildFlywayConfigurationExpression(classLoaderExpression, setterCalls);
            declaration.getVariable(0).setInitializer(replacement);

            removedIndices.sort((a, b) -> Integer.compare(b, a));
            for (int index : removedIndices) {
                block.getStatements().remove(index);
            }

            changed = true;
        }
        return changed;
    }

    private static boolean isFlywayType(com.github.javaparser.ast.type.Type type) {
        if (type.isClassOrInterfaceType()) {
            final ClassOrInterfaceType coi = type.asClassOrInterfaceType();
            final String name = coi.getNameWithScope();
            return FLYWAY_SIMPLE.equals(name) || FLYWAY_FQN.equals(name);
        }
        return FLYWAY_SIMPLE.equals(type.asString()) || FLYWAY_FQN.equals(type.asString());
    }

    private static boolean isDeprecatedFlywayConstruction(Expression expression) {
        return expression.isObjectCreationExpr() && isFlywayConstructor(expression.asObjectCreationExpr());
    }

    private static boolean isFlywayConstructor(ObjectCreationExpr creationExpr) {
        return creationExpr.getType().getNameWithScope().equals(FLYWAY_SIMPLE)
            || creationExpr.getType().getNameWithScope().equals(FLYWAY_FQN)
            || creationExpr.getType().asString().equals(FLYWAY_SIMPLE)
            || creationExpr.getType().asString().equals(FLYWAY_FQN);
    }

    private static boolean isCallOnVariable(MethodCallExpr call, String variableName) {
        return call.getScope().isPresent()
            && call.getScope().get().isNameExpr()
            && call.getScope().get().asNameExpr().getNameAsString().equals(variableName);
    }

    private static Expression buildFlywayConfigurationExpression(Expression classLoaderExpression, List<MethodCallExpr> setterCalls) {
        final String configureCall = classLoaderExpression == null
            ? FLYWAY_FQN + ".configure()"
            : FLYWAY_FQN + ".configure(" + classLoaderExpression + ")";

        Expression expression = StaticJavaParser.parseExpression(configureCall);
        for (MethodCallExpr setterCall : setterCalls) {
            final String methodName;
            switch (setterCall.getNameAsString()) {
                case "setDataSource":
                    methodName = "dataSource";
                    break;
                case "setLocations":
                    methodName = "locations";
                    break;
                case "setValidateOnMigrate":
                    methodName = "validateOnMigrate";
                    break;
                default:
                    throw new IllegalStateException("Unsupported Flyway setter: " + setterCall.getNameAsString());
            }

            final MethodCallExpr chained = new MethodCallExpr(expression, methodName);
            for (Expression arg : setterCall.getArguments()) {
                chained.addArgument(arg.clone());
            }
            expression = chained;
        }

        return new MethodCallExpr(expression, "load");
    }
}
