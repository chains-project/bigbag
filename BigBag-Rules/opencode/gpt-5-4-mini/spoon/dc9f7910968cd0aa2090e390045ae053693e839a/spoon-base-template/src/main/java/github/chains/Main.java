package github.chains;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import spoon.Launcher;
import spoon.reflect.code.CtAssignment;
import spoon.reflect.code.CtBlock;
import spoon.reflect.code.CtCodeSnippetExpression;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtLocalVariable;
import spoon.reflect.code.CtStatement;
import spoon.reflect.code.CtVariableRead;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtVariable;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

public class Main {
    private static final String FLYWAY = "org.flywaydb.core.Flyway";

    public static void main(String[] args) {
        if (args.length < 2) {
            throw new IllegalArgumentException("Usage: <input-source-dir> <output-source-dir>");
        }

        final File input = new File(args[0]);
        final File output = new File(args[1]);

        final Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setComplianceLevel(11);
        launcher.addInputResource(input.getAbsolutePath());
        launcher.setSourceOutputDirectory(output);

        launcher.buildModel();
        transformFlywayUsage(launcher);
        launcher.prettyprint();
    }

    private static void transformFlywayUsage(Launcher launcher) {
        for (CtLocalVariable<?> local : launcher.getModel().getElements(new TypeFilter<>(CtLocalVariable.class))) {
            transformLocalVariable(launcher, local);
        }
        for (CtAssignment<?, ?> assignment : launcher.getModel().getElements(new TypeFilter<>(CtAssignment.class))) {
            transformAssignment(launcher, assignment);
        }
        for (CtConstructorCall<?> constructorCall : launcher.getModel().getElements(new TypeFilter<>(CtConstructorCall.class))) {
            if (isFlywayConstructor(constructorCall)) {
                constructorCall.replace(createExpression(launcher, null, null, null, null));
            }
        }
    }

    private static void transformLocalVariable(Launcher launcher, CtLocalVariable<?> local) {
        if (!isFlywayType(local.getType()) || !(local.getDefaultExpression() instanceof CtConstructorCall)) {
            return;
        }
        final CtConstructorCall<?> constructorCall = (CtConstructorCall<?>) local.getDefaultExpression();
        if (!isFlywayConstructor(constructorCall)) {
            return;
        }

        final CtBlock<?> block = local.getParent(CtBlock.class);
        if (block == null) {
            local.setDefaultExpression((CtExpression) createExpression(launcher, null, null, null, null));
            return;
        }

        final int index = block.getStatements().indexOf(local);
        if (index < 0) {
            local.setDefaultExpression((CtExpression) createExpression(launcher, null, null, null, null));
            return;
        }

        final List<CtStatement> removable = new ArrayList<>();
        CtExpression<?> classLoader = null;
        CtInvocation<?> dataSource = null;
        CtInvocation<?> locations = null;
        CtInvocation<?> validate = null;

        for (int i = index + 1; i < block.getStatements().size(); i++) {
            final CtInvocation<?> invocation = asSetterInvocation(block.getStatements().get(i), local);
            if (invocation == null) {
                break;
            }
            removable.add(block.getStatements().get(i));
            switch (invocation.getExecutable().getSimpleName()) {
                case "setClassLoader":
                    classLoader = invocation.getArguments().isEmpty() ? null : invocation.getArguments().get(0);
                    break;
                case "setDataSource":
                    dataSource = invocation;
                    break;
                case "setLocations":
                    locations = invocation;
                    break;
                case "setValidateOnMigrate":
                    validate = invocation;
                    break;
                default:
                    break;
            }
        }

        local.setDefaultExpression((CtExpression) createExpression(launcher, classLoader, dataSource, locations, validate));
        removable.forEach(CtElement::delete);
    }

    private static void transformAssignment(Launcher launcher, CtAssignment<?, ?> assignment) {
        if (!(assignment.getAssignment() instanceof CtConstructorCall)) {
            return;
        }
        final CtConstructorCall<?> constructorCall = (CtConstructorCall<?>) assignment.getAssignment();
        if (!isFlywayConstructor(constructorCall)) {
            return;
        }

        final CtBlock<?> block = assignment.getParent(CtBlock.class);
        final CtExpression<?> assigned = assignment.getAssigned();
        if (!(assigned instanceof CtVariableRead) || block == null) {
            assignment.setAssignment((CtExpression) createExpression(launcher, null, null, null, null));
            return;
        }

        final CtVariable<?> variable = ((CtVariableRead<?>) assigned).getVariable().getDeclaration();
        if (variable == null) {
            assignment.setAssignment((CtExpression) createExpression(launcher, null, null, null, null));
            return;
        }

        final int index = block.getStatements().indexOf(assignment);
        if (index < 0) {
            assignment.setAssignment((CtExpression) createExpression(launcher, null, null, null, null));
            return;
        }

        final List<CtStatement> removable = new ArrayList<>();
        CtExpression<?> classLoader = null;
        CtInvocation<?> dataSource = null;
        CtInvocation<?> locations = null;
        CtInvocation<?> validate = null;

        for (int i = index + 1; i < block.getStatements().size(); i++) {
            final CtInvocation<?> invocation = asSetterInvocation(block.getStatements().get(i), variable);
            if (invocation == null) {
                break;
            }
            removable.add(block.getStatements().get(i));
            switch (invocation.getExecutable().getSimpleName()) {
                case "setClassLoader":
                    classLoader = invocation.getArguments().isEmpty() ? null : invocation.getArguments().get(0);
                    break;
                case "setDataSource":
                    dataSource = invocation;
                    break;
                case "setLocations":
                    locations = invocation;
                    break;
                case "setValidateOnMigrate":
                    validate = invocation;
                    break;
                default:
                    break;
            }
        }

        assignment.setAssignment((CtExpression) createExpression(launcher, classLoader, dataSource, locations, validate));
        removable.forEach(CtElement::delete);
    }

    private static CtInvocation<?> asSetterInvocation(CtStatement statement, CtVariable<?> variable) {
        if (!(statement instanceof CtInvocation)) {
            return null;
        }
        final CtInvocation<?> invocation = (CtInvocation<?>) statement;
        if (invocation.getTarget() == null || invocation.getExecutable() == null) {
            return null;
        }
        final String name = invocation.getExecutable().getSimpleName();
        if (!"setClassLoader".equals(name) && !"setDataSource".equals(name) && !"setLocations".equals(name) && !"setValidateOnMigrate".equals(name)) {
            return null;
        }
        if (!(invocation.getTarget() instanceof CtVariableRead)) {
            return null;
        }
        final CtVariable<?> target = ((CtVariableRead<?>) invocation.getTarget()).getVariable().getDeclaration();
        return target != null && target.equals(variable) ? invocation : null;
    }

    private static boolean isFlywayType(CtTypeReference<?> type) {
        return type != null && FLYWAY.equals(type.getQualifiedName());
    }

    private static boolean isFlywayConstructor(CtConstructorCall<?> constructorCall) {
        return constructorCall != null && constructorCall.getType() != null && FLYWAY.equals(constructorCall.getType().getQualifiedName()) && constructorCall.getArguments().isEmpty();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static CtExpression createExpression(Launcher launcher,
                                                 CtExpression<?> classLoader,
                                                 CtInvocation<?> dataSource,
                                                 CtInvocation<?> locations,
                                                 CtInvocation<?> validate) {
        final StringBuilder code = new StringBuilder();
        code.append("org.flywaydb.core.Flyway.configure(");
        if (classLoader != null) {
            code.append(classLoader);
        }
        code.append(")");
        if (dataSource != null) {
            appendCall(code, "dataSource", dataSource.getArguments());
        }
        if (locations != null) {
            appendCall(code, "locations", locations.getArguments());
        }
        if (validate != null) {
            appendCall(code, "validateOnMigrate", validate.getArguments());
        }
        code.append(".load()");
        return launcher.getFactory().Code().createCodeSnippetExpression(code.toString());
    }

    private static void appendCall(StringBuilder code, String name, List<? extends CtExpression<?>> args) {
        code.append('.').append(name).append('(');
        for (int i = 0; i < args.size(); i++) {
            if (i > 0) {
                code.append(", ");
            }
            code.append(args.get(i));
        }
        code.append(')');
    }
}
