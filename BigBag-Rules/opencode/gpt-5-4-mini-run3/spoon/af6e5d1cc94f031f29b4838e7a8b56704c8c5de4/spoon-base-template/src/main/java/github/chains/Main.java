package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtAssignment;
import spoon.reflect.code.CtBlock;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtLocalVariable;
import spoon.reflect.code.CtCodeSnippetStatement;
import spoon.reflect.code.CtStatement;
import spoon.reflect.code.CtVariableAccess;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class Main {
    private static final String FLYWAY_TYPE = "org.flywaydb.core.Flyway";
    private static final String CLASS_LOADER_SETTER = "setClassLoader";

    public static void main(String[] args) {
        final String inputPath = args.length > 0 ? args[0] : ".";
        final String outputPath = args.length > 1 ? args[1] : Paths.get("spoon-out").toString();

        final Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setComplianceLevel(11);
        launcher.addInputResource(inputPath);
        launcher.setSourceOutputDirectory(outputPath);
        launcher.buildModel();

        transform(launcher.getModel());
        launcher.prettyprint();
    }

    private static void transform(CtModel model) {
        for (CtBlock<?> block : model.getElements(new TypeFilter<>(CtBlock.class))) {
            rewriteBlock(block);
        }
    }

    private static void rewriteBlock(CtBlock<?> block) {
        final List<CtStatement> statements = new ArrayList<>(block.getStatements());
        final Set<CtStatement> statementsToDelete = new HashSet<>();

        for (CtStatement statement : statements) {
            final FlywaySite site = FlywaySite.from(statement);
            if (site == null) {
                continue;
            }

            final List<CtInvocation<?>> setters = new ArrayList<>();
            CtExpression<?> classLoaderArgument = null;

            for (CtStatement candidate : statements) {
                final CtInvocation<?> invocation = asSetterInvocation(candidate, site.variableName);
                if (invocation == null) {
                    continue;
                }

                if (CLASS_LOADER_SETTER.equals(invocation.getExecutable().getSimpleName()) && !invocation.getArguments().isEmpty()) {
                    classLoaderArgument = invocation.getArguments().get(0);
                    statementsToDelete.add(candidate);
                    continue;
                }

                setters.add(invocation);
                statementsToDelete.add(candidate);
            }

            final String replacement = buildFlywayStatement(site, classLoaderArgument, setters);
            final CtCodeSnippetStatement snippet = block.getFactory().Code().createCodeSnippetStatement(replacement);
            site.replaceStatement(snippet);
        }

        for (CtStatement statement : statementsToDelete) {
            statement.delete();
        }
    }

    private static CtInvocation<?> asSetterInvocation(CtStatement statement, String variableName) {
        if (!(statement instanceof CtInvocation<?> invocation)) {
            return null;
        }

        if (!isInvocationOnVariable(invocation, variableName)) {
            return null;
        }

        final String methodName = invocation.getExecutable().getSimpleName();
        return methodName.startsWith("set") ? invocation : null;
    }

    private static boolean isInvocationOnVariable(CtInvocation<?> invocation, String variableName) {
        final CtExpression<?> target = invocation.getTarget();
        if (!(target instanceof CtVariableAccess<?> variableAccess)) {
            return false;
        }
        return variableName.equals(variableAccess.getVariable().getSimpleName());
    }

    private static String buildFlywayStatement(FlywaySite site, CtExpression<?> classLoaderArgument, List<CtInvocation<?>> setters) {
        final StringBuilder expression = new StringBuilder();
        if (site.localVariable != null) {
            if (!site.localVariable.getModifiers().isEmpty()) {
                expression.append(site.localVariable.getModifiers().stream().map(Enum::name).map(String::toLowerCase).collect(Collectors.joining(" "))).append(' ');
            }
            expression.append(site.localVariable.getType().getQualifiedName()).append(' ').append(site.variableName).append(" = ");
        } else {
            expression.append(site.variableName).append(" = ");
        }

        expression.append("org.flywaydb.core.Flyway.configure(");
        if (classLoaderArgument != null) {
            expression.append(classLoaderArgument);
        }
        expression.append(")");

        for (CtInvocation<?> invocation : setters) {
            expression.append('.').append(fluentMethodName(invocation.getExecutable().getSimpleName()));
            expression.append('(');
            expression.append(invocation.getArguments().stream().map(Object::toString).collect(Collectors.joining(", ")));
            expression.append(')');
        }

        expression.append(".load()");
        expression.append(';');
        return expression.toString();
    }

    private static String fluentMethodName(String setterName) {
        if (!setterName.startsWith("set") || setterName.length() <= 3) {
            return setterName;
        }

        return Character.toLowerCase(setterName.charAt(3)) + setterName.substring(4);
    }

    private static final class FlywaySite {
        private final CtLocalVariable<?> localVariable;
        private final CtAssignment<?, ?> assignment;
        private final String variableName;

        private FlywaySite(CtLocalVariable<?> localVariable, CtAssignment<?, ?> assignment, String variableName) {
            this.localVariable = localVariable;
            this.assignment = assignment;
            this.variableName = variableName;
        }

        private static FlywaySite from(CtStatement statement) {
            if (statement instanceof CtLocalVariable<?> localVariable && isFlywayConstructor(localVariable.getDefaultExpression())) {
                return new FlywaySite(localVariable, null, localVariable.getSimpleName());
            }

            if (statement instanceof CtAssignment<?, ?> assignment && isFlywayConstructor(assignment.getAssignment())) {
                final CtExpression<?> leftHandSide = assignment.getAssigned();
                if (leftHandSide instanceof CtVariableAccess<?> variableAccess) {
                    return new FlywaySite(null, assignment, variableAccess.getVariable().getSimpleName());
                }
            }

            return null;
        }

        private void replaceStatement(CtCodeSnippetStatement statement) {
            if (this.localVariable != null) {
                this.localVariable.replace(statement);
                return;
            }

            if (this.assignment != null) {
                this.assignment.replace(statement);
            }
        }

        private static boolean isFlywayConstructor(CtExpression<?> expression) {
            if (!(expression instanceof CtConstructorCall<?> constructorCall)) {
                return false;
            }

            final CtTypeReference<?> type = constructorCall.getType();
            return type != null && FLYWAY_TYPE.equals(type.getQualifiedName()) && constructorCall.getArguments().isEmpty();
        }
    }
}
