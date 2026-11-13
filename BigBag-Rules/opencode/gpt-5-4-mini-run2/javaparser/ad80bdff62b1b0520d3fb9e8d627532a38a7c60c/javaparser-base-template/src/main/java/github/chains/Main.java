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
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class Main {

    private static final String FLYWAY_TYPE = "org.flywaydb.core.Flyway";
    private static final Map<String, String> SETTER_RENAMES = createSetterRenames();

    public static void main(String[] args) throws IOException {
        final Path sourceRoot = args.length > 0 ? Paths.get(args[0]) : Paths.get(".");
        Files.walk(sourceRoot)
                .filter(path -> path.toString().endsWith(".java"))
                .forEach(Main::transformFileSafely);
    }

    private static void transformFileSafely(Path path) {
        try {
            transformFile(path);
        } catch (IOException e) {
            throw new RuntimeException("Failed to transform " + path, e);
        }
    }

    private static void transformFile(Path path) throws IOException {
        final CompilationUnit compilationUnit = StaticJavaParser.parse(path);
        LexicalPreservingPrinter.setup(compilationUnit);

        compilationUnit.findAll(BlockStmt.class).forEach(Main::transformBlock);

        Files.writeString(path, LexicalPreservingPrinter.print(compilationUnit));
    }

    private static void transformBlock(BlockStmt block) {
        final NodeList<Statement> statements = block.getStatements();
        for (int i = 0; i < statements.size(); i++) {
            final Statement statement = statements.get(i);
            if (!(statement instanceof ExpressionStmt)) {
                continue;
            }

            final Expression expression = ((ExpressionStmt) statement).getExpression();
            if (!(expression instanceof VariableDeclarationExpr)) {
                continue;
            }

            final VariableDeclarationExpr declarationExpr = (VariableDeclarationExpr) expression;
            if (declarationExpr.getVariables().size() != 1) {
                continue;
            }

            final VariableDeclarator variable = declarationExpr.getVariable(0);
            if (!isFlywayType(variable.getType().asString())) {
                continue;
            }

            final Optional<ObjectCreationExpr> flywayConstruction = variable.getInitializer()
                    .filter(ObjectCreationExpr.class::isInstance)
                    .map(ObjectCreationExpr.class::cast)
                    .filter(Main::isOldFlywayConstruction);
            if (!flywayConstruction.isPresent()) {
                continue;
            }

            final String variableName = variable.getNameAsString();
            final List<MethodCallExpr> setterCalls = new ArrayList<>();
            int j = i + 1;
            while (j < statements.size()) {
                final Statement next = statements.get(j);
                final Optional<MethodCallExpr> call = extractSetterCall(next, variableName);
                if (!call.isPresent()) {
                    break;
                }
                setterCalls.add(call.get());
                j++;
            }

            variable.setInitializer(buildReplacementExpression(setterCalls));
            for (int removeIndex = j - 1; removeIndex > i; removeIndex--) {
                statements.remove(removeIndex);
            }
        }
    }

    private static Optional<MethodCallExpr> extractSetterCall(Statement statement, String variableName) {
        if (!(statement instanceof ExpressionStmt)) {
            return Optional.empty();
        }
        final Expression expression = ((ExpressionStmt) statement).getExpression();
        if (!(expression instanceof MethodCallExpr)) {
            return Optional.empty();
        }

        final MethodCallExpr call = (MethodCallExpr) expression;
        if (!call.getScope().isPresent() || !call.getScope().get().isNameExpr()) {
            return Optional.empty();
        }
        if (!call.getScope().get().asNameExpr().getNameAsString().equals(variableName)) {
            return Optional.empty();
        }
        if (!call.getNameAsString().startsWith("set")) {
            return Optional.empty();
        }
        return Optional.of(call);
    }

    private static Expression buildReplacementExpression(List<MethodCallExpr> setterCalls) {
        Expression expression = StaticJavaParser.parseExpression(FLYWAY_TYPE + ".configure()");
        for (MethodCallExpr setterCall : setterCalls) {
            final MethodCallExpr chainedCall = new MethodCallExpr(expression, renameSetter(setterCall.getNameAsString()));
            setterCall.getArguments().forEach(argument -> chainedCall.addArgument(argument.clone()));
            expression = chainedCall;
        }
        return new MethodCallExpr(expression, "load");
    }

    private static boolean isOldFlywayConstruction(ObjectCreationExpr creationExpr) {
        return isFlywayType(creationExpr.getType().asString()) && creationExpr.getArguments().isEmpty();
    }

    private static boolean isFlywayType(String typeName) {
        return FLYWAY_TYPE.equals(typeName) || typeName.endsWith(".Flyway") || "Flyway".equals(typeName);
    }

    private static String renameSetter(String setterName) {
        final String renamed = SETTER_RENAMES.get(setterName);
        if (renamed != null) {
            return renamed;
        }
        if (!setterName.startsWith("set") || setterName.length() <= 3) {
            return setterName;
        }
        final String remainder = setterName.substring(3);
        return Character.toLowerCase(remainder.charAt(0)) + remainder.substring(1);
    }

    private static Map<String, String> createSetterRenames() {
        final Map<String, String> renames = new HashMap<>();
        renames.put("setShouldCreateSchemas", "createSchemas");
        renames.put("setDryRunOutputAsFile", "dryRunOutput");
        renames.put("setDryRunOutputAsFileName", "dryRunOutput");
        renames.put("setEncodingAsString", "encoding");
        renames.put("setTargetAsString", "target");
        renames.put("setLocationsAsStrings", "locations");
        renames.put("setCallbacksAsClassNames", "callbacks");
        renames.put("setResolversAsClassNames", "resolvers");
        return renames;
    }
}
