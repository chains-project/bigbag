package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.AssignExpr;
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
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Main {
    private static final String FLYWAY_SIMPLE = "Flyway";
    private static final String FLYWAY_FQCN = "org.flywaydb.core.Flyway";

    public static void main(String[] args) throws IOException {
        if (args.length == 0) {
            throw new IllegalArgumentException("Expected a source root path");
        }

        final Path root = Paths.get(args[0]);
        final ParserConfiguration configuration = new ParserConfiguration();
        final JavaParser parser = new JavaParser(configuration);

        final List<Path> javaFiles;
        try (Stream<Path> paths = Files.walk(root)) {
            javaFiles = paths.filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                    .sorted(Comparator.naturalOrder())
                    .collect(Collectors.toList());
        }

        for (Path file : javaFiles) {
            final ParseResult<CompilationUnit> result = parser.parse(file);
            if (!result.isSuccessful() || !result.getResult().isPresent()) {
                continue;
            }

            final CompilationUnit compilationUnit = result.getResult().get();
            LexicalPreservingPrinter.setup(compilationUnit);

            if (transform(compilationUnit)) {
                Files.writeString(file, LexicalPreservingPrinter.print(compilationUnit), StandardCharsets.UTF_8);
            }
        }
    }

    private static boolean transform(CompilationUnit compilationUnit) {
        boolean changed = false;
        for (BlockStmt block : compilationUnit.findAll(BlockStmt.class)) {
            changed |= transformBlock(block);
        }
        return changed;
    }

    private static boolean transformBlock(BlockStmt block) {
        boolean changed = false;
        int index = 0;
        while (index < block.getStatements().size()) {
            final Statement statement = block.getStatement(index);
            final Optional<VariableDeclarator> declarator = findFlywayVariable(statement);
            if (declarator.isPresent()) {
                final FlywayReplacement replacement = collectReplacement(block, index, declarator.get().getNameAsString());
                if (replacement != null) {
                    declarator.get().setInitializer(StaticJavaParser.parseExpression(replacement.buildExpression()));
                    removeStatements(block, replacement.setterStatementIndices);
                    changed = true;
                }
            } else {
                final Optional<AssignExpr> assignment = findFlywayAssignment(statement);
                if (assignment.isPresent() && isFlywayCreation(assignment.get().getValue())) {
                    final String targetName = assignment.get().getTarget().toString();
                    final FlywayReplacement replacement = collectReplacement(block, index, targetName);
                    if (replacement != null) {
                        assignment.get().setValue(StaticJavaParser.parseExpression(replacement.buildExpression()));
                        removeStatements(block, replacement.setterStatementIndices);
                        changed = true;
                    }
                }
            }
            index++;
        }
        return changed;
    }

    private static Optional<VariableDeclarator> findFlywayVariable(Statement statement) {
        if (!statement.isExpressionStmt()) {
            return Optional.empty();
        }

        final Expression expression = statement.asExpressionStmt().getExpression();
        if (!expression.isVariableDeclarationExpr()) {
            return Optional.empty();
        }

        final VariableDeclarationExpr declarationExpr = expression.asVariableDeclarationExpr();
        if (declarationExpr.getVariables().size() != 1) {
            return Optional.empty();
        }

        final VariableDeclarator variable = declarationExpr.getVariable(0);
        return variable.getInitializer().filter(Main::isFlywayCreation).map(expr -> variable);
    }

    private static Optional<AssignExpr> findFlywayAssignment(Statement statement) {
        if (!statement.isExpressionStmt()) {
            return Optional.empty();
        }

        final Expression expression = statement.asExpressionStmt().getExpression();
        if (!expression.isAssignExpr()) {
            return Optional.empty();
        }

        return Optional.of(expression.asAssignExpr());
    }

    private static boolean isFlywayCreation(Expression expression) {
        if (!expression.isObjectCreationExpr()) {
            return false;
        }

        final ObjectCreationExpr creationExpr = expression.asObjectCreationExpr();
        final String typeName = creationExpr.getType().asString();
        return creationExpr.getArguments().isEmpty() && (FLYWAY_SIMPLE.equals(typeName) || FLYWAY_FQCN.equals(typeName)
                || typeName.endsWith("." + FLYWAY_SIMPLE));
    }

    private static FlywayReplacement collectReplacement(BlockStmt block, int startIndex, String variableName) {
        final List<Integer> setterStatementIndices = new ArrayList<>();
        final List<String> chainParts = new ArrayList<>();
        String classLoaderExpression = null;

        for (int i = startIndex + 1; i < block.getStatements().size(); i++) {
            final Statement statement = block.getStatement(i);
            if (!statement.isExpressionStmt()) {
                break;
            }

            final Expression expression = statement.asExpressionStmt().getExpression();
            if (!expression.isMethodCallExpr()) {
                break;
            }

            final MethodCallExpr call = expression.asMethodCallExpr();
            if (!isCallOnVariable(call, variableName)) {
                break;
            }

            final String mappedName = mapSetterName(call.getNameAsString());
            if (mappedName == null) {
                break;
            }

            setterStatementIndices.add(i);
            if ("configure".equals(mappedName)) {
                classLoaderExpression = call.getArguments().isEmpty() ? null : call.getArgument(0).toString();
            } else {
                chainParts.add(buildInvocation(mappedName, call));
            }
        }

        if (setterStatementIndices.isEmpty()) {
            return null;
        }

        return new FlywayReplacement(classLoaderExpression, chainParts, setterStatementIndices);
    }

    private static boolean isCallOnVariable(MethodCallExpr call, String variableName) {
        return call.getScope().isPresent() && call.getScope().get().isNameExpr()
                && variableName.equals(call.getScope().get().asNameExpr().getNameAsString());
    }

    private static String mapSetterName(String oldName) {
        if ("setDataSource".equals(oldName)) {
            return "dataSource";
        }
        if ("setLocations".equals(oldName)) {
            return "locations";
        }
        if ("setValidateOnMigrate".equals(oldName)) {
            return "validateOnMigrate";
        }
        if ("setClassLoader".equals(oldName)) {
            return "configure";
        }
        return null;
    }

    private static String buildInvocation(String methodName, MethodCallExpr call) {
        final String args = call.getArguments().stream().map(Node::toString).collect(Collectors.joining(", "));
        return "." + methodName + "(" + args + ")";
    }

    private static void removeStatements(BlockStmt block, List<Integer> indices) {
        final List<Integer> reversed = new ArrayList<>(indices);
        reversed.sort(Comparator.reverseOrder());
        for (int index : reversed) {
            block.getStatements().remove(index);
        }
    }

    private static final class FlywayReplacement {
        private final String classLoaderExpression;
        private final List<String> chainParts;
        private final List<Integer> setterStatementIndices;

        private FlywayReplacement(String classLoaderExpression, List<String> chainParts, List<Integer> setterStatementIndices) {
            this.classLoaderExpression = classLoaderExpression;
            this.chainParts = chainParts;
            this.setterStatementIndices = setterStatementIndices;
        }

        private String buildExpression() {
            final StringBuilder builder = new StringBuilder();
            builder.append(FLYWAY_FQCN).append(".configure(");
            if (classLoaderExpression != null) {
                builder.append(classLoaderExpression);
            }
            builder.append(")");
            for (String part : chainParts) {
                builder.append(part);
            }
            builder.append(".load()");
            return builder.toString();
        }
    }
}
