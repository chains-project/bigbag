package github.chains;

import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class Main {
    private static final String FLYWAY_FQCN = "org.flywaydb.core.Flyway";

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            throw new IllegalArgumentException("Expected a single source directory argument");
        }

        final Path sourceRoot = Paths.get(args[0]);
        if (!Files.isDirectory(sourceRoot)) {
            throw new IllegalArgumentException("Source directory does not exist: " + sourceRoot);
        }

        StaticJavaParser.setConfiguration(new ParserConfiguration().setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_11));

        Files.walk(sourceRoot)
                .filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                .forEach(Main::transformFile);
    }

    private static void transformFile(Path path) {
        try {
            final CompilationUnit cu = StaticJavaParser.parse(path);
            LexicalPreservingPrinter.setup(cu);

            final FlywayTransformer transformer = new FlywayTransformer();
            transformer.visit(cu, null);

            if (transformer.isChanged()) {
                Files.write(path, LexicalPreservingPrinter.print(cu).getBytes(StandardCharsets.UTF_8));
            }
        } catch (final Exception ex) {
            throw new RuntimeException("Failed to transform " + path, ex);
        }
    }

    private static final class FlywayTransformer {
        private boolean changed;

        boolean isChanged() {
            return this.changed;
        }

        void visit(CompilationUnit cu, Void unused) {
            cu.findAll(BlockStmt.class).forEach(this::transformBlock);
        }

        private void transformBlock(BlockStmt block) {
            final List<Statement> statements = new ArrayList<>(block.getStatements());
            for (int i = 0; i < statements.size(); i++) {
                final Statement statement = statements.get(i);

                final VariableTarget target = findVariableTarget(statement);
                if (target == null) {
                    continue;
                }

                final List<MethodCallExpr> setterCalls = collectSetterCalls(statements, i + 1, target.name);
                if (setterCalls.isEmpty()) {
                    continue;
                }

                final Expression transformed = buildFlywayChain(setterCalls);
                if (transformed == null) {
                    continue;
                }

                applyTransformation(statement, target, transformed);
                removeSetterStatements(block, statements, i + 1, setterCalls.size());
                this.changed = true;
            }
        }

        private VariableTarget findVariableTarget(Statement statement) {
            if (statement.isExpressionStmt()) {
                final Expression expr = statement.asExpressionStmt().getExpression();
                if (expr.isAssignExpr()) {
                    return findAssignedTarget(expr.asAssignExpr());
                }
            }

            if (!statement.isExpressionStmt()) {
                return null;
            }

            final Expression expr = statement.asExpressionStmt().getExpression();
            if (!expr.isVariableDeclarationExpr()) {
                return null;
            }

            final VariableDeclarationExpr declaration = expr.asVariableDeclarationExpr();
            if (declaration.getVariables().size() != 1) {
                return null;
            }

            final VariableDeclarator variable = declaration.getVariable(0);
            if (!isFlywayType(variable.getType().asString())) {
                return null;
            }

            if (!variable.getInitializer().isPresent() || !isZeroArgFlywayCreation(variable.getInitializer().get())) {
                return null;
            }

            return new VariableTarget(variable.getNameAsString(), TargetKind.DECLARATION);
        }

        private VariableTarget findAssignedTarget(AssignExpr assignExpr) {
            if (!assignExpr.getTarget().isNameExpr()) {
                return null;
            }

            if (!isZeroArgFlywayCreation(assignExpr.getValue())) {
                return null;
            }

            return new VariableTarget(assignExpr.getTarget().asNameExpr().getNameAsString(), TargetKind.ASSIGNMENT);
        }

        private boolean isFlywayType(String typeName) {
            return typeName.equals("Flyway") || typeName.endsWith(".Flyway");
        }

        private boolean isZeroArgFlywayCreation(Expression expression) {
            return expression.isObjectCreationExpr()
                    && expression.asObjectCreationExpr().getType().asString().endsWith("Flyway")
                    && expression.asObjectCreationExpr().getArguments().isEmpty();
        }

        private List<MethodCallExpr> collectSetterCalls(List<Statement> statements, int startIndex, String variableName) {
            final List<MethodCallExpr> setterCalls = new ArrayList<>();
            for (int i = startIndex; i < statements.size(); i++) {
                final Statement statement = statements.get(i);
                if (!statement.isExpressionStmt()) {
                    break;
                }

                final Expression expr = statement.asExpressionStmt().getExpression();
                if (!expr.isMethodCallExpr()) {
                    break;
                }

                final MethodCallExpr call = expr.asMethodCallExpr();
                if (!isSetterCallOnVariable(call, variableName)) {
                    break;
                }

                setterCalls.add(call);
            }
            return setterCalls;
        }

        private boolean isSetterCallOnVariable(MethodCallExpr call, String variableName) {
            return call.getScope().isPresent()
                    && call.getScope().get().isNameExpr()
                    && call.getScope().get().asNameExpr().getNameAsString().equals(variableName)
                    && call.getNameAsString().startsWith("set")
                    && call.getNameAsString().length() > 3;
        }

        private Expression buildFlywayChain(List<MethodCallExpr> setterCalls) {
            final MethodCallExpr classLoaderSetter = findSetter(setterCalls, "setClassLoader");
            Expression chain = classLoaderSetter != null
                    ? new MethodCallExpr(StaticJavaParser.parseExpression(FLYWAY_FQCN), "configure",
                            cloneArguments(classLoaderSetter))
                    : StaticJavaParser.parseExpression(FLYWAY_FQCN + ".configure()");

            for (final MethodCallExpr setterCall : setterCalls) {
                if (setterCall.getNameAsString().equals("setClassLoader")) {
                    continue;
                }

                final String fluentName = toFluentName(setterCall.getNameAsString());
                chain = new MethodCallExpr(chain, fluentName, cloneArguments(setterCall));
            }

            return new MethodCallExpr(chain, "load");
        }

        private MethodCallExpr findSetter(List<MethodCallExpr> setterCalls, String name) {
            for (final MethodCallExpr setterCall : setterCalls) {
                if (setterCall.getNameAsString().equals(name)) {
                    return setterCall;
                }
            }
            return null;
        }

        private NodeList<Expression> cloneArguments(MethodCallExpr call) {
            final NodeList<Expression> arguments = new NodeList<>();
            for (final Expression argument : call.getArguments()) {
                arguments.add(argument.clone());
            }
            return arguments;
        }

        private String toFluentName(String setterName) {
            final String stripped = setterName.substring(3);
            return Character.toLowerCase(stripped.charAt(0)) + stripped.substring(1);
        }

        private void applyTransformation(Statement statement, VariableTarget target, Expression transformed) {
            if (target.kind == TargetKind.DECLARATION) {
                final VariableDeclarationExpr declaration = statement.asExpressionStmt().getExpression().asVariableDeclarationExpr();
                declaration.getVariable(0).setInitializer(transformed);
            } else {
                statement.asExpressionStmt().getExpression().asAssignExpr().setValue(transformed);
            }
        }

        private void removeSetterStatements(BlockStmt block, List<Statement> statements, int startIndex, int count) {
            final Set<Integer> indexesToRemove = new HashSet<>();
            for (int i = 0; i < count; i++) {
                indexesToRemove.add(startIndex + i);
            }

            final List<Statement> updated = new ArrayList<>();
            for (int i = 0; i < statements.size(); i++) {
                if (!indexesToRemove.contains(i)) {
                    updated.add(statements.get(i));
                }
            }

            block.getStatements().clear();
            block.getStatements().addAll(updated);
        }

        private static final class VariableTarget {
            private final String name;
            private final TargetKind kind;

            private VariableTarget(String name, TargetKind kind) {
                this.name = name;
                this.kind = kind;
            }
        }

        private enum TargetKind {
            DECLARATION,
            ASSIGNMENT
        }
    }
}
