package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Stream;

public class Main {
    private static final String FLYWAY_FQCN = "org.flywaydb.core.Flyway";

    public static void main(String[] args) {
        final Path root = Paths.get(args.length > 0 ? args[0] : ".");

        try (Stream<Path> paths = Files.walk(root)) {
            paths.filter(Main::isJavaSource)
                    .forEach(Main::rewriteFile);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static boolean isJavaSource(final Path path) {
        final String normalized = path.toString();
        return Files.isRegularFile(path) && normalized.endsWith(".java") && !normalized.contains("/target/") && !normalized.contains("\\target\\");
    }

    private static void rewriteFile(final Path path) {
        try {
            final CompilationUnit compilationUnit = StaticJavaParser.parse(path);
            LexicalPreservingPrinter.setup(compilationUnit);

            if (rewriteFlywayBuilders(compilationUnit)) {
                ensureFlywayImport(compilationUnit);
                Files.writeString(path, LexicalPreservingPrinter.print(compilationUnit), StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static boolean rewriteFlywayBuilders(final CompilationUnit compilationUnit) {
        boolean changed = false;

        for (BlockStmt block : compilationUnit.findAll(BlockStmt.class)) {
            if (rewriteBlock(block)) {
                changed = true;
            }
        }

        return changed;
    }

    private static boolean rewriteBlock(final BlockStmt block) {
        boolean changed = false;

        for (int i = 0; i < block.getStatements().size(); i++) {
            final Statement statement = block.getStatement(i);
            final Optional<CreationSite> creationSite = findCreationSite(statement);
            if (!creationSite.isPresent()) {
                continue;
            }

            final String variableName = creationSite.get().variableName;
            final List<MethodCallExpr> fluentCalls = new ArrayList<>();
            Expression classLoaderArgument = null;

            int j = i + 1;
            while (j < block.getStatements().size()) {
                final Optional<SetterCall> setter = findSetterCall(block.getStatement(j), variableName);
                if (!setter.isPresent()) {
                    break;
                }

                if (setter.get().isClassLoaderSetter) {
                    classLoaderArgument = setter.get().arguments.get(0).clone();
                } else {
                    fluentCalls.add(setter.get().call);
                }
                j++;
            }

            creationSite.get().initializerUpdater.accept(buildFlywayChain(fluentCalls, classLoaderArgument));

            for (int removeIndex = i + 1; removeIndex < j; removeIndex++) {
                block.getStatements().remove(i + 1);
            }

            changed = true;
        }

        return changed;
    }

    private static Optional<CreationSite> findCreationSite(final Statement statement) {
        if (!statement.isExpressionStmt()) {
            return Optional.empty();
        }

        final Expression expression = statement.asExpressionStmt().getExpression();
        if (expression.isVariableDeclarationExpr()) {
            final VariableDeclarationExpr variableDeclarationExpr = expression.asVariableDeclarationExpr();
            if (variableDeclarationExpr.getVariables().size() != 1) {
                return Optional.empty();
            }

            final ObjectCreationExpr initializer = variableDeclarationExpr.getVariable(0).getInitializer().filter(ObjectCreationExpr.class::isInstance)
                    .map(ObjectCreationExpr.class::cast).orElse(null);
            if (initializer == null || !isFlywayCreation(initializer)) {
                return Optional.empty();
            }

            final String variableName = variableDeclarationExpr.getVariable(0).getNameAsString();
            return Optional.of(new CreationSite(variableName, replacement -> variableDeclarationExpr.getVariable(0).setInitializer(replacement)));
        }

        if (expression.isAssignExpr()) {
            final AssignExpr assignExpr = expression.asAssignExpr();
            if (!assignExpr.getTarget().isNameExpr()) {
                return Optional.empty();
            }

            final Optional<ObjectCreationExpr> initializer = assignExpr.getValue().toObjectCreationExpr();
            if (!initializer.isPresent() || !isFlywayCreation(initializer.get())) {
                return Optional.empty();
            }

            final String variableName = assignExpr.getTarget().asNameExpr().getNameAsString();
            return Optional.of(new CreationSite(variableName, replacement -> assignExpr.setValue(replacement)));
        }

        return Optional.empty();
    }

    private static Optional<SetterCall> findSetterCall(final Statement statement, final String variableName) {
        if (!statement.isExpressionStmt()) {
            return Optional.empty();
        }

        final Expression expression = statement.asExpressionStmt().getExpression();
        if (!expression.isMethodCallExpr()) {
            return Optional.empty();
        }

        final MethodCallExpr methodCallExpr = expression.asMethodCallExpr();
        if (!methodCallExpr.getScope().isPresent() || !methodCallExpr.getScope().get().isNameExpr()) {
            return Optional.empty();
        }

        final NameExpr scope = methodCallExpr.getScope().get().asNameExpr();
        if (!scope.getNameAsString().equals(variableName)) {
            return Optional.empty();
        }

        final String fluentName = toFluentName(methodCallExpr.getNameAsString());
        if (fluentName == null) {
            return Optional.empty();
        }

        final MethodCallExpr copy = new MethodCallExpr();
        copy.setName(fluentName);
        methodCallExpr.getArguments().forEach(argument -> copy.addArgument(argument.clone()));
        return Optional.of(new SetterCall(copy, "classLoader".equals(fluentName), new ArrayList<>(methodCallExpr.getArguments())));
    }

    private static Expression buildFlywayChain(final List<MethodCallExpr> calls, final Expression classLoaderArgument) {
        final MethodCallExpr configure = new MethodCallExpr(new NameExpr("Flyway"), "configure");
        if (classLoaderArgument != null) {
            configure.addArgument(classLoaderArgument.clone());
        }

        Expression expression = configure;

        for (MethodCallExpr call : calls) {
            final MethodCallExpr next = new MethodCallExpr(expression, call.getNameAsString());
            call.getArguments().forEach(argument -> next.addArgument(argument.clone()));
            expression = next;
        }

        return new MethodCallExpr(expression, "load");
    }

    private static boolean isFlywayCreation(final ObjectCreationExpr expression) {
        final String typeName = expression.getType().asString();
        return expression.getArguments().isEmpty() && (FLYWAY_FQCN.equals(typeName) || typeName.endsWith(".Flyway") || "Flyway".equals(typeName));
    }

    private static String toFluentName(final String setterName) {
        if (!setterName.startsWith("set") || setterName.length() <= 3) {
            return null;
        }

        final String stripped = setterName.substring(3);
        return Character.toLowerCase(stripped.charAt(0)) + stripped.substring(1);
    }

    private static void ensureFlywayImport(final CompilationUnit compilationUnit) {
        if (compilationUnit.getImports().stream().noneMatch(importDeclaration -> importDeclaration.getNameAsString().equals(FLYWAY_FQCN))) {
            compilationUnit.addImport(FLYWAY_FQCN);
        }
    }

    private static final class CreationSite {
        private final String variableName;
        private final Consumer<Expression> initializerUpdater;

        private CreationSite(final String variableName, final Consumer<Expression> initializerUpdater) {
            this.variableName = variableName;
            this.initializerUpdater = initializerUpdater;
        }
    }

    private static final class SetterCall {
        private final MethodCallExpr call;
        private final boolean isClassLoaderSetter;
        private final List<Expression> arguments;

        private SetterCall(final MethodCallExpr call, final boolean isClassLoaderSetter, final List<Expression> arguments) {
            this.call = call;
            this.isClassLoaderSetter = isClassLoaderSetter;
            this.arguments = arguments;
        }
    }
}
