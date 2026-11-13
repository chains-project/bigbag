package github.chains;

import spoon.Launcher;
import spoon.reflect.code.CtBlock;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtLocalVariable;
import spoon.reflect.code.CtStatement;
import spoon.reflect.code.CtVariableAccess;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class Main {
    private static final String FLYWAY_FQCN = "org.flywaydb.core.Flyway";

    public static void main(String[] args) {
        if (args.length < 2) {
            throw new IllegalArgumentException("Usage: <input-source-dir> <output-source-dir>");
        }

        final Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(true);
        launcher.getEnvironment().setComplianceLevel(17);

        launcher.addInputResource(args[0]);
        launcher.setSourceOutputDirectory(new File(args[1]));
        launcher.buildModel();

        rewriteFlywayBuilders(launcher);
        launcher.prettyprint();
    }

    private static void rewriteFlywayBuilders(final Launcher launcher) {
        final List<CtBlock<?>> blocks = launcher.getModel().getElements(new TypeFilter<>(CtBlock.class));
        for (final CtBlock<?> block : blocks) {
            rewriteBlock(block);
        }
    }

    private static void rewriteBlock(final CtBlock<?> block) {
        final List<CtStatement> statements = new ArrayList<>(block.getStatements());
        final Set<CtStatement> toDelete = new LinkedHashSet<>();

        for (int i = 0; i < statements.size(); i++) {
            final CtStatement statement = statements.get(i);
            final CtLocalVariable<?> variable = asFlywayLocalVariable(statement);
            if (variable == null) {
                continue;
            }

            final CtInvocation<?> classLoaderInvocation = classLoaderInvocation(statements, i, variable);
            final StringBuilder builder = new StringBuilder(FLYWAY_FQCN).append(".configure(");
            int j = i + 1;
            if (classLoaderInvocation != null) {
                builder.append(joinArguments(classLoaderInvocation));
                toDelete.add(statements.get(i + 1));
                j = i + 2;
            }
            builder.append(')');
            while (j < statements.size()) {
                final CtInvocation<?> invocation = asFlywaySetterInvocation(statements.get(j), variable);
                if (invocation == null) {
                    break;
                }
                final String mapped = mapSetter(invocation.getExecutable().getSimpleName());
                if (mapped == null) {
                    break;
                }
                builder.append('.').append(mapped);
                builder.append('(').append(joinArguments(invocation)).append(')');
                toDelete.add(statements.get(j));
                j++;
            }

            builder.append(".load()");
            variable.setDefaultExpression(block.getFactory().createCodeSnippetExpression(builder.toString()));
            i = j - 1;
        }

        for (final CtStatement statement : toDelete) {
            statement.delete();
        }
    }

    private static CtLocalVariable<?> asFlywayLocalVariable(final CtStatement statement) {
        if (!(statement instanceof CtLocalVariable<?> variable)) {
            return null;
        }
        final CtTypeReference<?> type = variable.getType();
        if (type == null || !FLYWAY_FQCN.equals(type.getQualifiedName())) {
            return null;
        }
        if (!(variable.getDefaultExpression() instanceof CtConstructorCall<?> constructorCall)) {
            return null;
        }
        return isOldFlywayConstructor(constructorCall) ? variable : null;
    }

    private static boolean isOldFlywayConstructor(final CtConstructorCall<?> constructorCall) {
        final CtTypeReference<?> declaringType = constructorCall.getExecutable().getDeclaringType();
        return declaringType != null
                && FLYWAY_FQCN.equals(declaringType.getQualifiedName())
                && constructorCall.getArguments().isEmpty();
    }

    private static CtInvocation<?> asFlywaySetterInvocation(final CtStatement statement, final CtLocalVariable<?> variable) {
        if (!(statement instanceof CtInvocation<?> invocation)) {
            return null;
        }
        final String methodName = invocation.getExecutable().getSimpleName();
        if (mapSetter(methodName) == null) {
            return null;
        }
        if (!(invocation.getTarget() instanceof CtVariableAccess<?> target)) {
            return null;
        }
        return target.getVariable() != null && variable.getReference().equals(target.getVariable()) ? invocation : null;
    }

    private static String mapSetter(final String methodName) {
        return switch (methodName) {
            case "setDataSource" -> "dataSource";
            case "setClassLoader" -> "classLoader";
            case "setLocations" -> "locations";
            case "setValidateOnMigrate" -> "validateOnMigrate";
            default -> null;
        };
    }

    private static CtInvocation<?> classLoaderInvocation(final List<CtStatement> statements, final int index, final CtLocalVariable<?> variable) {
        if (index + 1 >= statements.size()) {
            return null;
        }
        final CtInvocation<?> invocation = asFlywaySetterInvocation(statements.get(index + 1), variable);
        return invocation != null && "setClassLoader".equals(invocation.getExecutable().getSimpleName()) ? invocation : null;
    }

    private static String joinArguments(final CtInvocation<?> invocation) {
        final StringBuilder builder = new StringBuilder();
        for (int i = 0; i < invocation.getArguments().size(); i++) {
            if (i > 0) {
                builder.append(", ");
            }
            builder.append(invocation.getArguments().get(i));
        }
        return builder.toString();
    }
}
