package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ExpressionStmt;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

public class Main {
    public static void main(String[] args) {
        if (args.length == 0) {
            throw new IllegalArgumentException("Expected source root path");
        }

        final Path sourceRoot = Paths.get(args[0]);
        try (Stream<Path> paths = Files.walk(sourceRoot)) {
            paths.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .forEach(Main::transformFile);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void transformFile(Path path) {
        try {
            final CompilationUnit cu = StaticJavaParser.parse(path);
            boolean changed = false;

            for (BlockStmt block : cu.findAll(BlockStmt.class)) {
                changed |= transformBlock(block);
            }

            if (changed) {
                Files.write(path, cu.toString().getBytes(StandardCharsets.UTF_8));
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to transform " + path, e);
        }
    }

    private static boolean transformBlock(BlockStmt block) {
        final List<ExpressionStmt> declarationStatements = new ArrayList<>();
        for (Node node : block.getChildNodes()) {
            if (node instanceof ExpressionStmt) {
                final ExpressionStmt stmt = (ExpressionStmt) node;
                if (stmt.getExpression().isVariableDeclarationExpr()) {
                    declarationStatements.add(stmt);
                }
            }
        }

        boolean changed = false;
        for (ExpressionStmt declarationStmt : declarationStatements) {
            changed |= transformDeclaration(block, declarationStmt);
        }
        return changed;
    }

    private static boolean transformDeclaration(BlockStmt block, ExpressionStmt declarationStmt) {
        final VariableDeclarationExpr declaration = declarationStmt.getExpression().asVariableDeclarationExpr();
        if (declaration.getVariables().size() != 1) {
            return false;
        }

        final VariableDeclarator variable = declaration.getVariable(0);
        if (!isFlywayCreation(variable.getInitializer())) {
            return false;
        }

        final String variableName = variable.getNameAsString();
        final List<MethodCallExpr> setterCalls = new ArrayList<>();
        MethodCallExpr classLoaderCall = null;
        final Set<ExpressionStmt> removableStatements = new LinkedHashSet<>();

        final List<com.github.javaparser.ast.stmt.Statement> statements = new ArrayList<>(block.getStatements());
        final int declarationIndex = statements.indexOf(declarationStmt);
        if (declarationIndex < 0) {
            return false;
        }

        for (int i = declarationIndex + 1; i < statements.size(); i++) {
            final com.github.javaparser.ast.stmt.Statement statement = statements.get(i);
            if (statement.isExpressionStmt()) {
                final Expression expression = statement.asExpressionStmt().getExpression();
                if (expression.isMethodCallExpr()) {
                    final MethodCallExpr call = expression.asMethodCallExpr();
                    if (isMethodOnVariable(call, variableName) && isFlywaySetter(call.getNameAsString())) {
                        if ("setClassLoader".equals(call.getNameAsString())) {
                            classLoaderCall = call;
                        } else {
                            setterCalls.add(call);
                        }
                        removableStatements.add(statement.asExpressionStmt());
                        continue;
                    }
                }
            }

            if (statement.isReturnStmt()) {
                final Optional<Expression> returned = statement.asReturnStmt().getExpression();
                if (returned.isPresent() && returned.get().isNameExpr()
                        && variableName.equals(returned.get().asNameExpr().getNameAsString())) {
                    continue;
                }
            }

            if (statement.findAll(NameExpr.class).stream()
                    .anyMatch(name -> variableName.equals(name.getNameAsString()))) {
                return false;
            }
        }

        final StringBuilder builder = new StringBuilder();
        builder.append("org.flywaydb.core.Flyway.configure(");
        if (classLoaderCall != null) {
            builder.append(classLoaderCall.getArgument(0));
        }
        builder.append(")");

        for (MethodCallExpr call : setterCalls) {
            builder.append('.').append(mapSetterName(call.getNameAsString()));
            builder.append('(');
            for (int i = 0; i < call.getArguments().size(); i++) {
                if (i > 0) {
                    builder.append(", ");
                }
                builder.append(call.getArgument(i));
            }
            builder.append(')');
        }
        builder.append(".load()");

        variable.setInitializer(StaticJavaParser.parseExpression(builder.toString()));
        removableStatements.forEach(block.getStatements()::remove);
        return true;
    }

    private static boolean isFlywayCreation(Optional<Expression> initializer) {
        return initializer.isPresent() && initializer.get().isObjectCreationExpr()
                && isFlywayType(initializer.get().asObjectCreationExpr());
    }

    private static boolean isFlywayType(ObjectCreationExpr creationExpr) {
        return creationExpr.getType().getNameAsString().endsWith("Flyway") && creationExpr.getArguments().isEmpty();
    }

    private static boolean isMethodOnVariable(MethodCallExpr call, String variableName) {
        return call.getScope().isPresent() && call.getScope().get().isNameExpr()
                && variableName.equals(call.getScope().get().asNameExpr().getNameAsString());
    }

    private static boolean isFlywaySetter(String methodName) {
        return methodName.startsWith("set");
    }

    private static String mapSetterName(String methodName) {
        if ("setLocationsAsStrings".equals(methodName)) {
            return "locations";
        }
        if ("setCallbacksAsClassNames".equals(methodName)) {
            return "callbacks";
        }
        if ("setResolversAsClassNames".equals(methodName)) {
            return "resolvers";
        }
        if ("setTargetAsString".equals(methodName)) {
            return "target";
        }
        if ("setBaselineVersionAsString".equals(methodName)) {
            return "baselineVersion";
        }
        if ("setEncodingAsString".equals(methodName)) {
            return "encoding";
        }
        if ("setDryRunOutputAsFile".equals(methodName) || "setDryRunOutputAsFileName".equals(methodName)) {
            return "dryRunOutput";
        }
        final String suffix = methodName.substring(3);
        return Character.toLowerCase(suffix.charAt(0)) + suffix.substring(1);
    }
}
