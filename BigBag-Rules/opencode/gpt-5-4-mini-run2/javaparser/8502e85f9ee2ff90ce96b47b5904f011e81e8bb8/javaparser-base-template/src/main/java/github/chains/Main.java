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
import com.github.javaparser.ast.stmt.Statement;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class Main {
    private static final String FLYWAY_FQN = "org.flywaydb.core.Flyway";

    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            throw new IllegalArgumentException("Usage: Main <sourceDir> [outputDir]");
        }

        final Path inputRoot = Paths.get(args[0]).toAbsolutePath().normalize();
        final Path outputRoot = args.length > 1 ? Paths.get(args[1]).toAbsolutePath().normalize() : inputRoot;
        final List<Path> javaFiles;

        try (var stream = Files.walk(inputRoot)) {
            javaFiles = stream.filter(p -> Files.isRegularFile(p) && p.toString().endsWith(".java"))
                    .collect(Collectors.toList());
        }

        for (Path inputFile : javaFiles) {
            final CompilationUnit cu = StaticJavaParser.parse(inputFile, StandardCharsets.UTF_8);
            boolean changed = false;

            for (BlockStmt block : cu.findAll(BlockStmt.class)) {
                changed |= rewriteBlock(block);
            }

            final Path outputFile = outputRoot.equals(inputRoot) ? inputFile : outputRoot.resolve(inputRoot.relativize(inputFile));
            if (changed) {
                if (outputFile.getParent() != null) {
                    Files.createDirectories(outputFile.getParent());
                }
                Files.writeString(outputFile, cu.toString(), StandardCharsets.UTF_8);
            } else if (!outputRoot.equals(inputRoot)) {
                if (outputFile.getParent() != null) {
                    Files.createDirectories(outputFile.getParent());
                }
                Files.copy(inputFile, outputFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    private static boolean rewriteBlock(BlockStmt block) {
        final NodeList<Statement> rewritten = new NodeList<>();
        boolean changed = false;

        for (int i = 0; i < block.getStatements().size(); ) {
            final Statement statement = block.getStatement(i);
            final FlywaySequence sequence = matchFlywaySequence(block, i);
            if (sequence != null) {
                final Statement replacement = sequence.originalStatement.clone();
                replacement.asExpressionStmt().getExpression().asVariableDeclarationExpr().getVariable(0)
                        .setInitializer(StaticJavaParser.parseExpression(sequence.replacementExpression()));
                rewritten.add(replacement);
                i += sequence.consumedStatements;
                changed = true;
                continue;
            }

            rewritten.add(statement.clone());
            i++;
        }

        if (changed) {
            block.setStatements(rewritten);
        }

        return changed;
    }

    private static FlywaySequence matchFlywaySequence(BlockStmt block, int startIndex) {
        final Statement first = block.getStatement(startIndex);
        if (!first.isExpressionStmt()) {
            return null;
        }

        final VariableDeclarationExpr declarationExpr = first.asExpressionStmt().getExpression().asVariableDeclarationExpr();
        if (declarationExpr.getVariables().size() != 1) {
            return null;
        }
        final VariableDeclarator variable = declarationExpr.getVariable(0);

        if (!isFlywayType(variable) || variable.getInitializer().isEmpty()) {
            return null;
        }
        if (!isOldFlywayConstruction(variable.getInitializer().get())) {
            return null;
        }

        final String variableName = variable.getNameAsString();
        final List<MethodCallExpr> calls = new ArrayList<>();
        Expression classLoader = null;
        int index = startIndex + 1;

        while (index < block.getStatements().size()) {
            final Statement next = block.getStatement(index);
            if (!next.isExpressionStmt() || !next.asExpressionStmt().getExpression().isMethodCallExpr()) {
                break;
            }

            final MethodCallExpr call = next.asExpressionStmt().getExpression().asMethodCallExpr();
            if (!isCallOn(call, variableName) || call.getArguments().size() != 1) {
                break;
            }

            final String name = call.getNameAsString();
            if ("setDataSource".equals(name) || "setLocations".equals(name) || "setValidateOnMigrate".equals(name)) {
                calls.add(call);
            } else if ("setClassLoader".equals(name)) {
                classLoader = call.getArgument(0).clone();
            } else {
                break;
            }

            index++;
        }

        if (calls.isEmpty() && classLoader == null) {
            return null;
        }

        return new FlywaySequence(first, classLoader, calls, index - startIndex);
    }

    private static boolean isFlywayType(VariableDeclarator variable) {
        final String type = variable.getType().toString();
        return FLYWAY_FQN.equals(type) || "Flyway".equals(type);
    }

    private static boolean isOldFlywayConstruction(Expression expression) {
        if (!expression.isObjectCreationExpr()) {
            return false;
        }
        final ObjectCreationExpr creationExpr = expression.asObjectCreationExpr();
        final String type = creationExpr.getType().toString();
        return FLYWAY_FQN.equals(type) || "Flyway".equals(type);
    }

    private static boolean isCallOn(MethodCallExpr call, String variableName) {
        return call.getScope().isPresent()
                && call.getScope().get().isNameExpr()
                && variableName.equals(call.getScope().get().asNameExpr().getNameAsString());
    }

    private static final class FlywaySequence {
        private final Statement originalStatement;
        private final Expression classLoader;
        private final List<MethodCallExpr> calls;
        private final int consumedStatements;

        private FlywaySequence(Statement originalStatement, Expression classLoader, List<MethodCallExpr> calls, int consumedStatements) {
            this.originalStatement = originalStatement;
            this.classLoader = classLoader;
            this.calls = calls;
            this.consumedStatements = consumedStatements;
        }

        private String replacementExpression() {
            final StringBuilder builder = new StringBuilder();
            if (classLoader == null) {
                builder.append(FLYWAY_FQN).append(".configure()");
            } else {
                builder.append(FLYWAY_FQN).append(".configure(").append(classLoader).append(")");
            }

            for (MethodCallExpr call : calls) {
                final String name = call.getNameAsString();
                if ("setDataSource".equals(name)) {
                    builder.append(".dataSource(").append(call.getArgument(0)).append(")");
                } else if ("setLocations".equals(name)) {
                    builder.append(".locations(").append(call.getArgument(0)).append(")");
                } else if ("setValidateOnMigrate".equals(name)) {
                    builder.append(".validateOnMigrate(").append(call.getArgument(0)).append(")");
                }
            }

            builder.append(".load()");
            return builder.toString();
        }
    }
}
