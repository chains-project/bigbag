package github.chains;

import spoon.Launcher;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtStatement;
import spoon.reflect.code.CtVariableRead;
import spoon.reflect.code.CtLocalVariable;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class Main {
    private static final String FLYWAY_TYPE = "org.flywaydb.core.Flyway";

    public static void main(String[] args) {
        if (args.length < 1) {
            throw new IllegalArgumentException("Usage: Main <input-source-dir> [output-dir]");
        }

        final Path inputSourceDir = Path.of(args[0]).toAbsolutePath().normalize();
        final Path outputSourceDir = args.length > 1
                ? Path.of(args[1]).toAbsolutePath().normalize()
                : inputSourceDir;

        final Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(false);
        launcher.getEnvironment().setComplianceLevel(17);
        launcher.addInputResource(inputSourceDir.toString());
        launcher.setSourceOutputDirectory(new File(outputSourceDir.toString()));
        launcher.buildModel();

        final List<CtLocalVariable<?>> declarations = new ArrayList<>(launcher.getModel().getElements(new TypeFilter<>(CtLocalVariable.class)));
        declarations.forEach(Main::rewriteFlywayBlock);

        launcher.prettyprint();
    }

    private static void rewriteFlywayBlock(final CtLocalVariable<?> declaration) {
        if (!isOldFlywayConstruction(declaration)) {
            return;
        }

        if (!(declaration.getParent() instanceof spoon.reflect.code.CtBlock<?> block)) {
            return;
        }

        final List<CtStatement> statements = new ArrayList<>(block.getStatements());
        final int declarationIndex = statements.indexOf(declaration);
        if (declarationIndex < 0) {
            return;
        }

        final FlywayChain chain = new FlywayChain();
        for (int i = declarationIndex + 1; i < statements.size(); i++) {
            final CtStatement statement = statements.get(i);
            final CtInvocation<?> invocation = asFlywaySetterInvocation(statement, declaration);
            if (invocation == null || !chain.consume(invocation)) {
                break;
            }
            statement.delete();
        }

        if (chain.isEmpty()) {
            return;
        }

        @SuppressWarnings({"rawtypes", "unchecked"})
        final CtExpression rawExpression = chain.asExpression(declaration.getFactory());
        declaration.setDefaultExpression(rawExpression);
    }

    private static boolean isOldFlywayConstruction(final CtLocalVariable<?> declaration) {
        final CtTypeReference<?> type = declaration.getType();
        if (type == null || !FLYWAY_TYPE.equals(type.getQualifiedName())) {
            return false;
        }

        final CtExpression<?> defaultExpression = declaration.getDefaultExpression();
        if (!(defaultExpression instanceof CtConstructorCall<?> constructorCall)) {
            return false;
        }

        final CtTypeReference<?> constructedType = constructorCall.getType();
        return constructedType != null && FLYWAY_TYPE.equals(constructedType.getQualifiedName()) && constructorCall.getArguments().isEmpty();
    }

    private static CtInvocation<?> asFlywaySetterInvocation(final CtStatement statement, final CtLocalVariable<?> declaration) {
        if (!(statement instanceof CtInvocation<?> invocation)) {
            return null;
        }

        final CtExecutableReference<?> executable = invocation.getExecutable();
        if (executable == null || !FlywayChain.isSupportedSetter(executable.getSimpleName())) {
            return null;
        }

        final CtExpression<?> target = invocation.getTarget();
        if (!(target instanceof CtVariableRead<?> variableRead) || variableRead.getVariable() == null) {
            return null;
        }

        return declaration.getSimpleName().equals(variableRead.getVariable().getSimpleName()) ? invocation : null;
    }

    private static final class FlywayChain {
        private CtExpression<?> classLoader;
        private CtExpression<?> dataSource;
        private CtExpression<?> locations;
        private CtExpression<?> validateOnMigrate;

        static boolean isSupportedSetter(final String methodName) {
            return "setClassLoader".equals(methodName)
                    || "setDataSource".equals(methodName)
                    || "setLocations".equals(methodName)
                    || "setLocationsAsStrings".equals(methodName)
                    || "setValidateOnMigrate".equals(methodName);
        }

        boolean consume(final CtInvocation<?> invocation) {
            if (invocation.getArguments().isEmpty()) {
                return false;
            }

            final String methodName = invocation.getExecutable().getSimpleName();
            final CtExpression<?> argument = invocation.getArguments().get(0);
            switch (methodName) {
                case "setClassLoader" -> this.classLoader = argument;
                case "setDataSource" -> this.dataSource = argument;
                case "setLocations", "setLocationsAsStrings" -> this.locations = argument;
                case "setValidateOnMigrate" -> this.validateOnMigrate = argument;
                default -> {
                    return false;
                }
            }
            return true;
        }

        boolean isEmpty() {
            return this.classLoader == null && this.dataSource == null && this.locations == null && this.validateOnMigrate == null;
        }

        CtExpression<?> asExpression(final spoon.reflect.factory.Factory factory) {
            final StringBuilder expression = new StringBuilder();
            if (this.classLoader != null) {
                expression.append("org.flywaydb.core.Flyway.configure(").append(this.classLoader).append(')');
            } else {
                expression.append("org.flywaydb.core.Flyway.configure()");
            }

            if (this.dataSource != null) {
                expression.append(".dataSource(").append(this.dataSource).append(')');
            }
            if (this.locations != null) {
                expression.append(".locations(").append(this.locations).append(')');
            }
            if (this.validateOnMigrate != null) {
                expression.append(".validateOnMigrate(").append(this.validateOnMigrate).append(')');
            }

            expression.append(".load()");
            return factory.Code().createCodeSnippetExpression(expression.toString());
        }
    }
}
