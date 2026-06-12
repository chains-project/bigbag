package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.InitializerDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.ThisExpr;
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
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

public class Main {

    private static final Set<String> SETTER_NAMES = new HashSet<>(Arrays.asList(
            "setDataSource",
            "setLocations",
            "setValidateOnMigrate",
            "setClassLoader"
    ));

    public static void main(String[] args) {
        if (args.length != 1) {
            throw new IllegalArgumentException("Expected a single source root path argument");
        }

        Path sourceRoot = Paths.get(args[0]);
        try (Stream<Path> paths = Files.walk(sourceRoot)) {
            paths.filter(path -> path.toString().endsWith(".java")).forEach(Main::processFile);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void processFile(Path file) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(file);
            LexicalPreservingPrinter.setup(cu);

            if (transformCompilationUnit(cu)) {
                Files.write(file, LexicalPreservingPrinter.print(cu).getBytes(StandardCharsets.UTF_8));
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to process " + file, e);
        }
    }

    private static boolean transformCompilationUnit(CompilationUnit cu) {
        boolean changed = false;

        for (MethodDeclaration method : cu.findAll(MethodDeclaration.class)) {
            if (method.getBody().isPresent()) {
                changed |= transformBlock(method.getBody().get());
            }
        }

        for (ConstructorDeclaration constructor : cu.findAll(ConstructorDeclaration.class)) {
            changed |= transformBlock(constructor.getBody());
        }

        for (InitializerDeclaration initializer : cu.findAll(InitializerDeclaration.class)) {
            changed |= transformBlock(initializer.getBody());
        }

        return changed;
    }

    private static boolean transformBlock(BlockStmt block) {
        boolean changed = false;
        List<Statement> statements = block.getStatements();

        for (int i = 0; i < statements.size(); i++) {
            Statement statement = statements.get(i);
            if (!statement.isExpressionStmt()) {
                continue;
            }

            VariableDeclarator declarator = findFlywayDeclarator(statement);
            if (declarator == null || !declarator.getInitializer().isPresent()) {
                continue;
            }

            ObjectCreationExpr creation = declarator.getInitializer().get().asObjectCreationExpr();
            if (!isNoArgFlywayCreation(creation)) {
                continue;
            }

            List<SetterInvocation> invocations = new ArrayList<>();
            Expression classLoaderExpression = null;
            int j = i + 1;
            while (j < statements.size()) {
                Statement next = statements.get(j);
                Optional<SetterInvocation> invocation = matchSetterInvocation(next, declarator.getNameAsString());
                if (!invocation.isPresent()) {
                    break;
                }

                SetterInvocation setterInvocation = invocation.get();
                if ("setClassLoader".equals(setterInvocation.methodName)) {
                    classLoaderExpression = setterInvocation.arguments.get(0).clone();
                } else {
                    invocations.add(setterInvocation);
                }
                statements.remove(j);
                changed = true;
            }

            declarator.setInitializer(buildFlywayLoadExpression(classLoaderExpression, invocations));
            changed = true;
        }

        return changed;
    }

    private static VariableDeclarator findFlywayDeclarator(Statement statement) {
        if (!statement.isExpressionStmt()) {
            return null;
        }

        Expression expression = statement.asExpressionStmt().getExpression();
        if (!expression.isVariableDeclarationExpr()) {
            return null;
        }

        VariableDeclarationExpr variableDeclarationExpr = expression.asVariableDeclarationExpr();
        if (variableDeclarationExpr.getVariables().size() != 1) {
            return null;
        }

        VariableDeclarator declarator = variableDeclarationExpr.getVariable(0);
        String typeName = declarator.getType().toString();
        if (!("Flyway".equals(typeName) || typeName.endsWith(".Flyway"))) {
            return null;
        }

        return declarator;
    }

    private static boolean isNoArgFlywayCreation(ObjectCreationExpr creation) {
        String typeName = creation.getType().toString();
        return ("Flyway".equals(typeName) || typeName.endsWith(".Flyway")) && creation.getArguments().isEmpty();
    }

    private static Optional<SetterInvocation> matchSetterInvocation(Statement statement, String variableName) {
        if (!statement.isExpressionStmt()) {
            return Optional.empty();
        }

        Expression expression = statement.asExpressionStmt().getExpression();
        if (!expression.isMethodCallExpr()) {
            return Optional.empty();
        }

        MethodCallExpr call = expression.asMethodCallExpr();
        if (!SETTER_NAMES.contains(call.getNameAsString()) || !call.getScope().isPresent()) {
            return Optional.empty();
        }

        Expression scope = call.getScope().get();
        if (!matchesVariableScope(scope, variableName)) {
            return Optional.empty();
        }

        return Optional.of(new SetterInvocation(call.getNameAsString(), cloneArguments(call.getArguments()), statement));
    }

    private static boolean matchesVariableScope(Expression scope, String variableName) {
        if (scope.isNameExpr()) {
            return variableName.equals(scope.asNameExpr().getNameAsString());
        }
        if (scope.isFieldAccessExpr()) {
            FieldAccessExpr fieldAccessExpr = scope.asFieldAccessExpr();
            return variableName.equals(fieldAccessExpr.getNameAsString())
                    && fieldAccessExpr.getScope().isThisExpr();
        }
        if (scope.isThisExpr()) {
            return false;
        }
        return variableName.equals(scope.toString()) || (scope.toString().startsWith("this.") && variableName.equals(scope.toString().substring(5)));
    }

    private static NodeList<Expression> cloneArguments(NodeList<Expression> expressions) {
        NodeList<Expression> cloned = new NodeList<>();
        for (Expression expression : expressions) {
            cloned.add(expression.clone());
        }
        return cloned;
    }

    private static Expression buildFlywayLoadExpression(Expression classLoaderExpression, List<SetterInvocation> invocations) {
        MethodCallExpr configure = classLoaderExpression == null
                ? new MethodCallExpr(new NameExpr("Flyway"), "configure")
                : new MethodCallExpr(new NameExpr("Flyway"), "configure", new NodeList<>(classLoaderExpression));

        Expression current = configure;
        for (SetterInvocation invocation : invocations) {
            String fluentName = toFluentName(invocation.methodName);
            if (fluentName == null) {
                continue;
            }
            current = new MethodCallExpr(current, fluentName, invocation.arguments);
        }

        return new MethodCallExpr(current, "load");
    }

    private static String toFluentName(String methodName) {
        switch (methodName) {
            case "setDataSource":
                return "dataSource";
            case "setLocations":
                return "locations";
            case "setValidateOnMigrate":
                return "validateOnMigrate";
            default:
                return null;
        }
    }

    private static final class SetterInvocation {
        private final String methodName;
        private final NodeList<Expression> arguments;
        @SuppressWarnings("unused")
        private final Statement sourceStatement;

        private SetterInvocation(String methodName, NodeList<Expression> arguments, Statement sourceStatement) {
            this.methodName = methodName;
            this.arguments = arguments;
            this.sourceStatement = sourceStatement;
        }
    }
}
