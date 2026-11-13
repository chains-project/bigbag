package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class Main {
    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            throw new IllegalArgumentException("Usage: Main <source-root> [output-root]");
        }

        final Path inputRoot = Paths.get(args[0]);
        final Path outputRoot = args.length > 1 ? Paths.get(args[1]) : inputRoot;

        final ParserConfiguration configuration = new ParserConfiguration()
                .setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_11);
        final JavaParser parser = new JavaParser(configuration);

        try (var paths = Files.walk(inputRoot)) {
            final List<Path> javaFiles = paths
                    .filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                    .sorted(Comparator.naturalOrder())
                    .collect(Collectors.toList());

            for (Path inputFile : javaFiles) {
                final CompilationUnit compilationUnit = parser.parse(inputFile).getResult()
                        .orElseThrow(() -> new IllegalStateException("Failed to parse " + inputFile));
                LexicalPreservingPrinter.setup(compilationUnit);

                if (transform(compilationUnit)) {
                    final Path outputFile = outputRoot.equals(inputRoot)
                            ? inputFile
                            : outputRoot.resolve(inputRoot.relativize(inputFile));
                    final Path parent = outputFile.getParent();
                    if (parent != null) {
                        Files.createDirectories(parent);
                    }
                    Files.writeString(outputFile, LexicalPreservingPrinter.print(compilationUnit), StandardCharsets.UTF_8);
                }
            }
        }
    }

    private static boolean transform(final CompilationUnit compilationUnit) {
        boolean changed = false;
        for (BlockStmt blockStmt : compilationUnit.findAll(BlockStmt.class)) {
            changed |= transformBlock(blockStmt);
        }
        return changed;
    }

    private static boolean transformBlock(final BlockStmt blockStmt) {
        final Map<String, FlywayUsage> usages = new LinkedHashMap<>();
        final List<Statement> statements = new ArrayList<>(blockStmt.getStatements());

        for (Statement statement : statements) {
            if (!statement.isExpressionStmt()) {
                continue;
            }

            final Expression expression = statement.asExpressionStmt().getExpression();
            if (!expression.isVariableDeclarationExpr()) {
                continue;
            }

            for (VariableDeclarator declarator : expression.asVariableDeclarationExpr().getVariables()) {
                if (isOldFlywayConstruction(declarator)) {
                    usages.put(declarator.getNameAsString(), new FlywayUsage(declarator));
                }
            }
        }

        for (Statement statement : statements) {
            if (!statement.isExpressionStmt()) {
                continue;
            }

            final Expression expression = statement.asExpressionStmt().getExpression();
            if (!expression.isMethodCallExpr()) {
                continue;
            }

            final MethodCallExpr call = expression.asMethodCallExpr();
            final String target = targetName(call);
            final FlywayUsage usage = usages.get(target);
            if (usage == null) {
                continue;
            }

            final String methodName = call.getNameAsString();
            if ("setClassLoader".equals(methodName) && call.getArguments().size() == 1) {
                usage.classLoader = call.getArgument(0);
                usage.removals.add(statement);
                continue;
            }

            if (methodName.startsWith("set")) {
                usage.setters.add(new SetterInvocation(statement, methodName, new NodeList<>(call.getArguments())));
                usage.removals.add(statement);
            }
        }

        boolean changed = false;
        for (FlywayUsage usage : usages.values()) {
            if (usage.declarator.getInitializer().isEmpty()) {
                continue;
            }

            final Expression replacement = buildReplacement(usage);
            if (replacement == null) {
                continue;
            }

            usage.declarator.setInitializer(replacement);
            changed = true;
        }

        if (!changed) {
            return false;
        }

        for (FlywayUsage usage : usages.values()) {
            for (Statement removal : usage.removals) {
                removal.remove();
            }
        }

        return true;
    }

    private static String targetName(final MethodCallExpr call) {
        if (call.getScope().isEmpty() || !call.getScope().get().isNameExpr()) {
            return null;
        }
        return call.getScope().get().asNameExpr().getNameAsString();
    }

    private static boolean isOldFlywayConstruction(final VariableDeclarator declarator) {
        if (!declarator.getType().isClassOrInterfaceType()) {
            return false;
        }
        if (!"Flyway".equals(declarator.getType().asClassOrInterfaceType().getNameAsString())) {
            return false;
        }
        if (declarator.getInitializer().isEmpty()) {
            return false;
        }

        final Expression initializer = declarator.getInitializer().get();
        return initializer.isObjectCreationExpr()
                && "Flyway".equals(initializer.asObjectCreationExpr().getType().getNameAsString())
                && initializer.asObjectCreationExpr().getArguments().isEmpty();
    }

    private static Expression buildReplacement(final FlywayUsage usage) {
        final MethodCallExpr configure = usage.classLoader == null
                ? new MethodCallExpr(new NameExpr("Flyway"), "configure")
                : new MethodCallExpr(new NameExpr("Flyway"), "configure", NodeList.nodeList(usage.classLoader));

        Expression chain = configure;
        for (SetterInvocation setter : usage.setters) {
            final String builderMethod = builderMethod(setter.methodName);
            if (builderMethod == null) {
                continue;
            }

            chain = new MethodCallExpr(chain, builderMethod, setter.arguments);
        }

        return new MethodCallExpr(chain, "load");
    }

    private static String builderMethod(final String setterMethodName) {
        switch (setterMethodName) {
            case "setDataSource":
                return "dataSource";
            case "setLocations":
                return "locations";
            case "setValidateOnMigrate":
                return "validateOnMigrate";
            case "setShouldCreateSchemas":
                return "createSchemas";
            default:
                break;
        }

        if (!setterMethodName.startsWith("set") || setterMethodName.length() <= 3) {
            return null;
        }

        final String suffix = setterMethodName.substring(3);
        return Character.toLowerCase(suffix.charAt(0)) + suffix.substring(1);
    }

    private static final class FlywayUsage {
        private final VariableDeclarator declarator;
        private final List<SetterInvocation> setters = new ArrayList<>();
        private final Set<Statement> removals = new LinkedHashSet<>();
        private Expression classLoader;

        private FlywayUsage(final VariableDeclarator declarator) {
            this.declarator = declarator;
        }
    }

    private static final class SetterInvocation {
        @SuppressWarnings("unused")
        private final Statement statement;
        private final String methodName;
        private final NodeList<Expression> arguments;

        private SetterInvocation(final Statement statement, final String methodName, final NodeList<Expression> arguments) {
            this.statement = statement;
            this.methodName = methodName;
            this.arguments = arguments;
        }
    }
}
