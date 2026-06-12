package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.Statement;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Main {
    private static final String FLYWAY_FQCN = "org.flywaydb.core.Flyway";

    public static void main(String[] args) throws IOException {
        if (args.length < 1 || args.length > 2) {
            throw new IllegalArgumentException("Usage: Main <input-source-dir> [output-source-dir]");
        }

        final Path inputRoot = Paths.get(args[0]);
        final Path outputRoot = args.length == 2 ? Paths.get(args[1]) : inputRoot;

        final List<Path> javaFiles;
        try (Stream<Path> stream = Files.walk(inputRoot)) {
            javaFiles = stream.filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                    .collect(Collectors.toList());
        }

        for (Path inputFile : javaFiles) {
            final Path outputFile = outputRoot.resolve(inputRoot.relativize(inputFile));
            final CompilationUnit compilationUnit = StaticJavaParser.parse(inputFile);
            final boolean changed = transform(compilationUnit);

            if (changed || !inputFile.equals(outputFile)) {
                if (outputFile.getParent() != null) {
                    Files.createDirectories(outputFile.getParent());
                }
                Files.writeString(outputFile, compilationUnit.toString());
            }
        }
    }

    private static boolean transform(final CompilationUnit compilationUnit) {
        boolean changed = false;
        for (BlockStmt blockStmt : compilationUnit.findAll(BlockStmt.class)) {
            changed = transformBlock(blockStmt) || changed;
        }
        return changed;
    }

    private static boolean transformBlock(final BlockStmt blockStmt) {
        final List<Statement> originalStatements = new ArrayList<>(blockStmt.getStatements());
        final List<Statement> rewrittenStatements = new ArrayList<>();
        boolean changed = false;

        for (int i = 0; i < originalStatements.size(); i++) {
            final Statement statement = originalStatements.get(i);
            final FlywayDeclaration declaration = matchFlywayDeclaration(statement);
            if (declaration == null) {
                rewrittenStatements.add(statement);
                continue;
            }

            final List<SetterCall> setters = new ArrayList<>();
            int nextIndex = i + 1;
            while (nextIndex < originalStatements.size()) {
                final SetterCall setterCall = matchSetterCall(originalStatements.get(nextIndex), declaration.variableName);
                if (setterCall == null) {
                    break;
                }
                setters.add(setterCall);
                nextIndex++;
            }

            declaration.variableDeclarator.setInitializer(buildConfigurationExpression(setters));
            rewrittenStatements.add(statement);
            i = nextIndex - 1;
            changed = true;
        }

        if (changed) {
            blockStmt.setStatements(new NodeList<>(rewrittenStatements));
        }
        return changed;
    }

    private static FlywayDeclaration matchFlywayDeclaration(final Statement statement) {
        if (!statement.isExpressionStmt()) {
            return null;
        }

        final Expression expression = statement.asExpressionStmt().getExpression();
        if (!expression.isVariableDeclarationExpr()) {
            return null;
        }

        final VariableDeclarationExpr variableDeclarationExpr = expression.asVariableDeclarationExpr();
        if (variableDeclarationExpr.getVariables().size() != 1) {
            return null;
        }

        final VariableDeclarator variableDeclarator = variableDeclarationExpr.getVariable(0);
        if (!isFlywayType(variableDeclarator.getType().toString())) {
            return null;
        }

        if (!variableDeclarator.getInitializer().isPresent() || !variableDeclarator.getInitializer().get().isObjectCreationExpr()) {
            return null;
        }

        final ObjectCreationExpr objectCreationExpr = variableDeclarator.getInitializer().get().asObjectCreationExpr();
        if (!isFlywayType(objectCreationExpr.getType().toString())) {
            return null;
        }

        return new FlywayDeclaration(variableDeclarator.getNameAsString(), variableDeclarator);
    }

    private static SetterCall matchSetterCall(final Statement statement, final String variableName) {
        if (!statement.isExpressionStmt()) {
            return null;
        }

        final Expression expression = statement.asExpressionStmt().getExpression();
        if (!expression.isMethodCallExpr()) {
            return null;
        }

        final MethodCallExpr methodCallExpr = expression.asMethodCallExpr();
        if (!methodCallExpr.getScope().isPresent()) {
            return null;
        }

        final Expression scope = methodCallExpr.getScope().get();
        final boolean sameVariable = scope.isNameExpr() && scope.asNameExpr().getNameAsString().equals(variableName);
        final boolean sameThisVariable = scope.isFieldAccessExpr() && isThisField(scope.asFieldAccessExpr(), variableName);
        if (!sameVariable && !sameThisVariable) {
            return null;
        }

        final String mappedName = mapSetterName(methodCallExpr.getNameAsString());
        if (mappedName == null) {
            return null;
        }

        return new SetterCall(methodCallExpr.getNameAsString(), mappedName,
                new ArrayList<>(methodCallExpr.getArguments()));
    }

    private static Expression buildConfigurationExpression(final List<SetterCall> setters) {
        Expression expression = new MethodCallExpr(parseClassReference(FLYWAY_FQCN), "configure");
        for (SetterCall setter : setters) {
            if ("setClassLoader".equals(setter.originalName)) {
                expression = new MethodCallExpr(parseClassReference(FLYWAY_FQCN), "configure",
                        new NodeList<>(cloneArguments(setter.arguments)));
                break;
            }
        }

        for (SetterCall setter : setters) {
            if ("setClassLoader".equals(setter.originalName)) {
                continue;
            }
            expression = new MethodCallExpr(expression, setter.mappedName, new NodeList<>(cloneArguments(setter.arguments)));
        }

        return new MethodCallExpr(expression, "load");
    }

    private static Expression parseClassReference(final String fqcn) {
        return StaticJavaParser.parseExpression(fqcn);
    }

    private static List<Expression> cloneArguments(final List<Expression> arguments) {
        final List<Expression> clonedArguments = new ArrayList<>(arguments.size());
        for (Expression argument : arguments) {
            clonedArguments.add(argument.clone());
        }
        return clonedArguments;
    }

    private static boolean isThisField(final FieldAccessExpr fieldAccessExpr, final String variableName) {
        return fieldAccessExpr.getScope().isThisExpr() && fieldAccessExpr.getNameAsString().equals(variableName);
    }

    private static boolean isFlywayType(final String typeName) {
        return FLYWAY_FQCN.equals(typeName) || "Flyway".equals(typeName);
    }

    private static String mapSetterName(final String setterName) {
        if ("setDataSource".equals(setterName)) {
            return "dataSource";
        }
        if ("setLocations".equals(setterName) || "setLocationsAsStrings".equals(setterName)) {
            return "locations";
        }
        if ("setValidateOnMigrate".equals(setterName)) {
            return "validateOnMigrate";
        }
        if ("setClassLoader".equals(setterName)) {
            return "configure";
        }
        return null;
    }

    private static final class FlywayDeclaration {
        private final String variableName;
        private final VariableDeclarator variableDeclarator;

        private FlywayDeclaration(final String variableName, final VariableDeclarator variableDeclarator) {
            this.variableName = variableName;
            this.variableDeclarator = variableDeclarator;
        }
    }

    private static final class SetterCall {
        private final String originalName;
        private final String mappedName;
        private final List<Expression> arguments;

        private SetterCall(final String originalName, final String mappedName, final List<Expression> arguments) {
            this.originalName = originalName;
            this.mappedName = mappedName;
            this.arguments = arguments;
        }
    }
}
