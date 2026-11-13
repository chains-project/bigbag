package github.chains;

import spoon.Launcher;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtLocalVariable;
import spoon.reflect.code.CtStatement;
import spoon.reflect.code.CtVariableRead;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtVariableReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class Main {
    private static final String FLYWAY = "org.flywaydb.core.Flyway";
    private static final Set<String> OLD_METHODS = new HashSet<>(Arrays.asList(
            "setDataSource",
            "setClassLoader",
            "setLocations",
            "setLocationsAsStrings",
            "setValidateOnMigrate"
    ));

    private Main() {
    }

    public static void main(final String[] args) {
        final Path input = Paths.get(args.length > 0 ? args[0] : ".");
        final Path output = Paths.get(args.length > 1 ? args[1] : input.resolveSibling(input.getFileName() + "-out").toString());

        final Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(false);
        launcher.addInputResource(input.toString());
        launcher.setSourceOutputDirectory(output.toFile());
        launcher.buildModel();

        final Factory factory = launcher.getFactory();
        for (CtElement element : launcher.getModel().getElements(new TypeFilter<>(spoon.reflect.code.CtBlock.class))) {
            transformBlock((spoon.reflect.code.CtBlock<?>) element, factory);
        }

        launcher.prettyprint();
    }

    private static void transformBlock(final spoon.reflect.code.CtBlock<?> block, final Factory factory) {
        final List<CtStatement> statements = block.getStatements();
        for (int i = 0; i < statements.size(); i++) {
            final CtStatement statement = statements.get(i);
            if (!(statement instanceof CtLocalVariable)) {
                continue;
            }

            final Match match = matchSequence((CtLocalVariable<?>) statement, statements, i);
            if (match == null) {
                continue;
            }

            statements.set(i, factory.Code().createCodeSnippetStatement(match.replacement));
            for (int j = match.endIndex; j > i; j--) {
                statements.remove(j);
            }
        }
    }

    private static Match matchSequence(final CtLocalVariable<?> variable, final List<CtStatement> statements, final int startIndex) {
        final CtExpression<?> assignment = variable.getAssignment();
        if (!(assignment instanceof CtConstructorCall)) {
            return null;
        }

        final CtConstructorCall<?> constructorCall = (CtConstructorCall<?>) assignment;
        if (!FLYWAY.equals(constructorCall.getType().getQualifiedName()) || !constructorCall.getArguments().isEmpty()) {
            return null;
        }

        final String variableName = variable.getSimpleName();
        String classLoaderArg = null;
        String dataSourceArg = null;
        String locationsArg = null;
        String validateOnMigrateArg = null;

        int endIndex = startIndex;
        for (int i = startIndex + 1; i < statements.size(); i++) {
            final CtStatement statement = statements.get(i);
            if (!(statement instanceof CtInvocation)) {
                break;
            }

            final CtInvocation<?> invocation = (CtInvocation<?>) statement;
            if (!isInvocationOnVariable(invocation, variableName)) {
                break;
            }

            final String method = invocation.getExecutable().getSimpleName();
            if (!OLD_METHODS.contains(method)) {
                break;
            }

            if ("setClassLoader".equals(method)) {
                classLoaderArg = firstArg(invocation);
            } else if ("setDataSource".equals(method)) {
                dataSourceArg = args(invocation);
            } else if ("setLocations".equals(method) || "setLocationsAsStrings".equals(method)) {
                locationsArg = args(invocation);
            } else if ("setValidateOnMigrate".equals(method)) {
                validateOnMigrateArg = firstArg(invocation);
            }

            endIndex = i;
        }

        if (dataSourceArg == null && locationsArg == null && validateOnMigrateArg == null) {
            return null;
        }

        final StringBuilder builder = new StringBuilder();
        if (variable.hasModifier(spoon.reflect.declaration.ModifierKind.FINAL)) {
            builder.append("final ");
        }
        builder.append(FLYWAY).append(' ').append(variableName).append(" = ");
        builder.append(FLYWAY).append(".configure(");
        if (classLoaderArg != null) {
            builder.append(classLoaderArg);
        }
        builder.append(")");
        if (dataSourceArg != null) {
            builder.append(".dataSource(").append(dataSourceArg).append(")");
        }
        if (locationsArg != null) {
            builder.append(".locations(").append(locationsArg).append(")");
        }
        if (validateOnMigrateArg != null) {
            builder.append(".validateOnMigrate(").append(validateOnMigrateArg).append(")");
        }
        builder.append(".load();");

        return new Match(builder.toString(), endIndex);
    }

    private static boolean isInvocationOnVariable(final CtInvocation<?> invocation, final String variableName) {
        final CtExpression<?> target = invocation.getTarget();
        if (!(target instanceof CtVariableRead)) {
            return false;
        }
        final CtVariableReference<?> reference = ((CtVariableRead<?>) target).getVariable();
        return reference != null && variableName.equals(reference.getSimpleName());
    }

    private static String firstArg(final CtInvocation<?> invocation) {
        return invocation.getArguments().isEmpty() ? "" : invocation.getArguments().get(0).toString();
    }

    private static String args(final CtInvocation<?> invocation) {
        final List<String> rendered = new ArrayList<>();
        for (CtExpression<?> argument : invocation.getArguments()) {
            rendered.add(argument.toString());
        }
        return String.join(", ", rendered);
    }

    private static final class Match {
        private final String replacement;
        private final int endIndex;

        private Match(final String replacement, final int endIndex) {
            this.replacement = replacement;
            this.endIndex = endIndex;
        }
    }
}
