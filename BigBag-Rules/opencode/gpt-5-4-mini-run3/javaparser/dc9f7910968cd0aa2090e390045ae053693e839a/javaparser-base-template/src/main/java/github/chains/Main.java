package github.chains;

import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.SimpleName;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class Main {
    private static final String FLYWAY_TYPE_SUFFIX = "Flyway";

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            throw new IllegalArgumentException("Usage: Main <source-root>");
        }

        StaticJavaParser.setConfiguration(new ParserConfiguration().setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_11));

        final Path sourceRoot = Paths.get(args[0]);
        try (var paths = Files.walk(sourceRoot)) {
            paths.filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                    .filter(path -> !path.toString().contains("/target/"))
                    .forEach(Main::transformFile);
        }
    }

    private static void transformFile(Path path) {
        try {
            final CompilationUnit cu = StaticJavaParser.parse(path);
            if (transformCompilationUnit(cu)) {
                Files.writeString(path, cu.toString(), StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to transform " + path, e);
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
        final List<Statement> statements = block.getStatements();

        int index = 0;
        while (index < statements.size()) {
            final Statement statement = statements.get(index);
            final Optional<Candidate> candidate = Candidate.from(statement);
            if (candidate.isEmpty()) {
                index++;
                continue;
            }

            int end = index + 1;
            final List<SetterCall> setterCalls = new ArrayList<>();
            while (end < statements.size()) {
                final Optional<SetterCall> setterCall = SetterCall.from(statements.get(end), candidate.get().variableName);
                if (setterCall.isEmpty()) {
                    break;
                }
                setterCalls.add(setterCall.get());
                end++;
            }

            final Expression replacement = buildReplacement(candidate.get().flywayTypeName, setterCalls);
            candidate.get().replaceInitializer(replacement);

            for (int i = end - 1; i > index; i--) {
                statements.remove(i);
            }

            changed = true;
            index++;
        }

        return changed;
    }

    private static Expression buildReplacement(String flywayTypeName, List<SetterCall> setterCalls) {
        final SetterCall classLoader = setterCalls.stream().filter(SetterCall::isClassLoader).findFirst().orElse(null);
        final StringBuilder expression = new StringBuilder();
        expression.append(flywayTypeName).append(".configure(");
        if (classLoader != null && !classLoader.arguments.isEmpty()) {
            expression.append(classLoader.arguments.get(0));
        }
        expression.append(")");

        for (SetterCall setterCall : setterCalls) {
            if (setterCall.isClassLoader()) {
                continue;
            }
            expression.append('.').append(setterCall.fluentMethodName()).append('(');
            for (int i = 0; i < setterCall.arguments.size(); i++) {
                if (i > 0) {
                    expression.append(", ");
                }
                expression.append(setterCall.arguments.get(i));
            }
            expression.append(')');
        }

        expression.append(".load()");
        return StaticJavaParser.parseExpression(expression.toString());
    }

    private static boolean isFlywayType(String typeName) {
        return typeName != null && typeName.endsWith(FLYWAY_TYPE_SUFFIX);
    }

    private static final class Candidate {
        private final String variableName;
        private final String flywayTypeName;
        private final VariableDeclarator variableDeclarator;
        private final AssignExpr assignment;

        private Candidate(String variableName, String flywayTypeName, VariableDeclarator variableDeclarator, AssignExpr assignment) {
            this.variableName = variableName;
            this.flywayTypeName = flywayTypeName;
            this.variableDeclarator = variableDeclarator;
            this.assignment = assignment;
        }

        private static Optional<Candidate> from(Statement statement) {
            if (!(statement instanceof ExpressionStmt)) {
                return Optional.empty();
            }

            final Expression expression = ((ExpressionStmt) statement).getExpression();
            if (expression instanceof VariableDeclarationExpr) {
                for (VariableDeclarator variableDeclarator : ((VariableDeclarationExpr) expression).getVariables()) {
                    final Optional<Expression> initializer = variableDeclarator.getInitializer();
                    if (initializer.isPresent() && isFlywayConstruction(initializer.get())) {
                        return Optional.of(new Candidate(variableDeclarator.getNameAsString(), getFlywayTypeName(initializer.get()), variableDeclarator, null));
                    }
                }
            }

            if (expression instanceof AssignExpr) {
                final AssignExpr assignExpr = (AssignExpr) expression;
                if (assignExpr.getTarget() instanceof NameExpr && isFlywayConstruction(assignExpr.getValue())) {
                    return Optional.of(new Candidate(((NameExpr) assignExpr.getTarget()).getNameAsString(), getFlywayTypeName(assignExpr.getValue()), null, assignExpr));
                }
            }

            return Optional.empty();
        }

        private void replaceInitializer(Expression replacement) {
            if (this.variableDeclarator != null) {
                this.variableDeclarator.setInitializer(replacement);
                return;
            }

            if (this.assignment != null) {
                this.assignment.setValue(replacement);
            }
        }

        private static boolean isFlywayConstruction(Expression expression) {
            if (!(expression instanceof ObjectCreationExpr)) {
                return false;
            }
            return isFlywayType(((ObjectCreationExpr) expression).getType().asString());
        }

        private static String getFlywayTypeName(Expression expression) {
            return ((ObjectCreationExpr) expression).getType().asString();
        }
    }

    private static final class SetterCall {
        private final String methodName;
        private final List<Expression> arguments;

        private SetterCall(String methodName, List<Expression> arguments) {
            this.methodName = methodName;
            this.arguments = arguments;
        }

        private static Optional<SetterCall> from(Statement statement, String variableName) {
            if (!(statement instanceof ExpressionStmt)) {
                return Optional.empty();
            }

            final Expression expression = ((ExpressionStmt) statement).getExpression();
            if (!(expression instanceof MethodCallExpr)) {
                return Optional.empty();
            }

            final MethodCallExpr methodCallExpr = (MethodCallExpr) expression;
            if (methodCallExpr.getScope().isEmpty() || !(methodCallExpr.getScope().get() instanceof NameExpr)) {
                return Optional.empty();
            }

            final NameExpr scope = (NameExpr) methodCallExpr.getScope().get();
            if (!variableName.equals(scope.getNameAsString())) {
                return Optional.empty();
            }

            final String methodName = methodCallExpr.getNameAsString();
            if (!isSupportedSetter(methodName)) {
                return Optional.empty();
            }

            final List<Expression> arguments = new ArrayList<>();
            methodCallExpr.getArguments().forEach(argument -> arguments.add(argument.clone()));
            return Optional.of(new SetterCall(methodName, arguments));
        }

        private static boolean isSupportedSetter(String methodName) {
            return "setDataSource".equals(methodName)
                    || "setClassLoader".equals(methodName)
                    || "setLocations".equals(methodName)
                    || "setValidateOnMigrate".equals(methodName);
        }

        private boolean isClassLoader() {
            return "setClassLoader".equals(this.methodName);
        }

        private String fluentMethodName() {
            if (!this.methodName.startsWith("set") || this.methodName.length() <= 3) {
                return this.methodName;
            }
            return Character.toLowerCase(this.methodName.charAt(3)) + this.methodName.substring(4);
        }
    }
}
