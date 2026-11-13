package github.chains;

import java.util.ArrayList;
import java.util.List;

import spoon.Launcher;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtLocalVariable;
import spoon.reflect.code.CtStatement;
import spoon.reflect.code.CtVariableAccess;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

public class Main {
    private static final String FLYWAY_TYPE = "org.flywaydb.core.Flyway";

    public static void main(String[] args) {
        if (args.length < 2) {
            throw new IllegalArgumentException("Expected input and output source directories");
        }

        final Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.addInputResource(args[0]);
        launcher.setSourceOutputDirectory(args[1]);
        launcher.buildModel();

        final CtTypeReference<?> flywayType = launcher.getFactory().Type().createReference(FLYWAY_TYPE);

        for (CtMethod<?> method : launcher.getModel().getElements(new TypeFilter<>(CtMethod.class))) {
            transformMethod(method, flywayType, launcher);
        }

        launcher.prettyprint();
    }

    private static void transformMethod(final CtMethod<?> method, final CtTypeReference<?> flywayType,
            final Launcher launcher) {
        if (method.getBody() == null) {
            return;
        }

        final List<CtStatement> original = new ArrayList<>(method.getBody().getStatements());
        final List<CtStatement> rewritten = new ArrayList<>();

        for (int i = 0; i < original.size(); i++) {
            final CtStatement statement = original.get(i);

            if (statement instanceof CtLocalVariable<?> localVariable
                    && isFlywayConstructor(localVariable, flywayType)) {
                final RewritePlan plan = buildRewritePlan(localVariable, original, i + 1, flywayType);
                localVariable.setDefaultExpression(
                        launcher.getFactory().Code().createCodeSnippetExpression(plan.expression));
                rewritten.add(localVariable);
                i = plan.nextIndex - 1;
                continue;
            }

            rewritten.add(statement);
        }

        method.getBody().getStatements().clear();
        method.getBody().getStatements().addAll(rewritten);
    }

    private static boolean isFlywayConstructor(final CtLocalVariable<?> localVariable,
            final CtTypeReference<?> flywayType) {
        if (localVariable.getType() == null || localVariable.getDefaultExpression() == null) {
            return false;
        }
        if (!flywayType.getQualifiedName().equals(localVariable.getType().getQualifiedName())) {
            return false;
        }
        if (!(localVariable.getDefaultExpression() instanceof CtConstructorCall<?> ctor)) {
            return false;
        }
        return ctor.getType() != null && flywayType.getQualifiedName().equals(ctor.getType().getQualifiedName())
                && ctor.getArguments().isEmpty();
    }

    private static RewritePlan buildRewritePlan(final CtLocalVariable<?> localVariable, final List<CtStatement> statements,
            final int startIndex, final CtTypeReference<?> flywayType) {
        final String variableName = localVariable.getSimpleName();
        final List<String> chain = new ArrayList<>();
        chain.add(FLYWAY_TYPE + ".configure()");

        int index = startIndex;
        while (index < statements.size()) {
            final CtStatement statement = statements.get(index);
            if (!(statement instanceof CtInvocation<?> invocation) || !invocationTargetsVariable(invocation, variableName)) {
                break;
            }

            final String methodName = invocation.getExecutable().getSimpleName();
            if ("setClassLoader".equals(methodName) && invocation.getArguments().size() == 1) {
                chain.set(0, FLYWAY_TYPE + ".configure(" + invocation.getArguments().get(0) + ")");
                index++;
                continue;
            }

            if ("setDataSource".equals(methodName)) {
                chain.add(".dataSource(" + joinArguments(invocation.getArguments()) + ")");
                index++;
                continue;
            }

            if ("setLocations".equals(methodName) || "setLocationsAsStrings".equals(methodName)) {
                chain.add(".locations(" + joinArguments(invocation.getArguments()) + ")");
                index++;
                continue;
            }

            if ("setValidateOnMigrate".equals(methodName) && invocation.getArguments().size() == 1) {
                chain.add(".validateOnMigrate(" + invocation.getArguments().get(0) + ")");
                index++;
                continue;
            }

            break;
        }

        chain.add(".load()");
        return new RewritePlan(String.join("", chain), index);
    }

    private static boolean invocationTargetsVariable(final CtInvocation<?> invocation, final String variableName) {
        final CtExpression<?> target = invocation.getTarget();
        if (!(target instanceof CtVariableAccess<?> variableAccess) || variableAccess.getVariable() == null) {
            return false;
        }
        return variableName.equals(variableAccess.getVariable().getSimpleName());
    }

    private static String joinArguments(final List<CtExpression<?>> arguments) {
        final StringBuilder builder = new StringBuilder();
        for (int i = 0; i < arguments.size(); i++) {
            if (i > 0) {
                builder.append(", ");
            }
            builder.append(arguments.get(i));
        }
        return builder.toString();
    }

    private static final class RewritePlan {
        private final String expression;
        private final int nextIndex;

        private RewritePlan(final String expression, final int nextIndex) {
            this.expression = expression;
            this.nextIndex = nextIndex;
        }
    }
}
