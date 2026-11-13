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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class Main {
    private static final String FLYWAY_FQCN = "org.flywaydb.core.Flyway";
    private static final Set<String> SUPPORTED_SETTERS = new HashSet<>(Arrays.asList(
        "setDataSource",
        "setClassLoader",
        "setLocations",
        "setValidateOnMigrate"
    ));

    public static void main(String[] args) throws IOException {
        final Path root = args.length > 0 ? Paths.get(args[0]) : Paths.get(".");
        final List<Path> sources = new ArrayList<>();
        try (var walk = Files.walk(root)) {
            walk.filter(p -> Files.isRegularFile(p) && p.toString().endsWith(".java")).forEach(sources::add);
        }

        for (final Path source : sources) {
            final CompilationUnit cu = StaticJavaParser.parse(source);
            LexicalPreservingPrinter.setup(cu);
            if (transform(cu)) {
                Files.writeString(source, LexicalPreservingPrinter.print(cu));
            }
        }
    }

    private static boolean transform(final CompilationUnit cu) {
        boolean changed = false;
        for (final BlockStmt block : cu.findAll(BlockStmt.class)) {
            changed |= transformBlock(block);
        }
        return changed;
    }

    private static boolean transformBlock(final BlockStmt block) {
        final List<Statement> statements = block.getStatements();
        boolean changed = false;

        for (int i = 0; i < statements.size(); i++) {
            final Statement statement = statements.get(i);
            if (!statement.isExpressionStmt()) {
                continue;
            }

            final VariableDeclarator variable = extractFlywayVariable(statement);
            if (variable == null) {
                continue;
            }

            final List<Integer> removable = new ArrayList<>();
            final List<MethodCallExpr> chainCalls = new ArrayList<>();
            Expression classLoaderArgument = null;

            for (int j = i + 1; j < statements.size(); j++) {
                final Statement candidate = statements.get(j);
                if (!candidate.isExpressionStmt()) {
                    break;
                }

                final Expression candidateExpression = candidate.asExpressionStmt().getExpression();
                if (!candidateExpression.isMethodCallExpr()) {
                    break;
                }

                final MethodCallExpr methodCall = candidateExpression.asMethodCallExpr();
                if (!isCallOnVariable(methodCall, variable.getNameAsString())) {
                    break;
                }

                final String methodName = methodCall.getNameAsString();
                if (!SUPPORTED_SETTERS.contains(methodName)) {
                    break;
                }

                removable.add(j);
                if ("setClassLoader".equals(methodName)) {
                    classLoaderArgument = methodCall.getArgument(0).clone();
                    continue;
                }

                chainCalls.add(methodCall);
            }

            if (removable.isEmpty()) {
                continue;
            }

            variable.setInitializer(buildFlywayChain(classLoaderArgument, chainCalls));
            for (int k = removable.size() - 1; k >= 0; k--) {
                statements.remove((int) removable.get(k));
            }
            changed = true;
        }

        return changed;
    }

    private static VariableDeclarator extractFlywayVariable(final Statement statement) {
        if (statement.isExpressionStmt() && statement.asExpressionStmt().getExpression().isVariableDeclarationExpr()) {
            final VariableDeclarationExpr declarationExpr = statement.asExpressionStmt().getExpression().asVariableDeclarationExpr();
            if (declarationExpr.getVariables().size() != 1) {
                return null;
            }
            final VariableDeclarator variable = declarationExpr.getVariable(0);
            return isOldFlywayConstruction(variable) ? variable : null;
        } else {
            return null;
        }
    }

    private static boolean isOldFlywayConstruction(final VariableDeclarator variable) {
        if (!variable.getType().asString().endsWith("Flyway")) {
            return false;
        }
        if (variable.getInitializer().isEmpty()) {
            return false;
        }

        final Expression initializer = variable.getInitializer().get();
        return initializer.isObjectCreationExpr() && isFlywayCreation(initializer.asObjectCreationExpr());
    }

    private static boolean isFlywayCreation(final ObjectCreationExpr creationExpr) {
        return creationExpr.getType().asString().endsWith("Flyway") && creationExpr.getArguments().isEmpty();
    }

    private static boolean isCallOnVariable(final MethodCallExpr methodCall, final String variableName) {
        if (methodCall.getScope().isEmpty()) {
            return false;
        }
        final Expression scope = methodCall.getScope().get();
        return scope.isNameExpr() && scope.asNameExpr().getNameAsString().equals(variableName);
    }

    private static MethodCallExpr buildFlywayChain(final Expression classLoaderArgument, final List<MethodCallExpr> calls) {
        MethodCallExpr current = classLoaderArgument == null
            ? parsedFlywayConfigure()
            : new MethodCallExpr(parsedFlywayType(), "configure", new NodeList<>(classLoaderArgument));

        for (final MethodCallExpr call : calls) {
            final MethodCallExpr next = new MethodCallExpr(current, toFluentMethodName(call.getNameAsString()));
            for (final Expression argument : call.getArguments()) {
                next.addArgument(argument.clone());
            }
            current = next;
        }

        return new MethodCallExpr(current, "load");
    }

    private static Expression parsedFlywayType() {
        return StaticJavaParser.parseExpression("org.flywaydb.core.Flyway");
    }

    private static MethodCallExpr parsedFlywayConfigure() {
        return StaticJavaParser.parseExpression("org.flywaydb.core.Flyway.configure()").asMethodCallExpr();
    }

    private static String toFluentMethodName(final String legacyName) {
        if (legacyName.startsWith("set") && legacyName.length() > 3) {
            final String raw = legacyName.substring(3);
            return Character.toLowerCase(raw.charAt(0)) + raw.substring(1);
        }
        return legacyName;
    }

}
