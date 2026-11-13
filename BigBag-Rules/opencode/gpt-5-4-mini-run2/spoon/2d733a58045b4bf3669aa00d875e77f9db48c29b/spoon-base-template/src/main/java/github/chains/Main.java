package github.chains;

import spoon.Launcher;
import spoon.reflect.code.CtAssignment;
import spoon.reflect.code.CtBlock;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtLocalVariable;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtType;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtTypeReference;

import java.io.File;

public class Main {
    private static final String FLYWAY_TYPE = "org.flywaydb.core.Flyway";

    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Usage: Main <source-directory>");
            System.exit(1);
        }

        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.addInputResource(args[0]);
        launcher.buildModel();

        transform(launcher);

        launcher.getEnvironment().setSourceOutputDirectory(new File(args[0]));
        launcher.prettyprint();
    }

    static void transform(Launcher launcher) {
        CtTypeReference<?> flywayType = launcher.getFactory().Type().createReference(FLYWAY_TYPE);

        for (CtType<?> type : launcher.getModel().getAllTypes()) {
            for (CtMethod<?> method : type.getMethods()) {
                CtBlock<?> body = method.getBody();
                if (body == null) {
                    continue;
                }

                for (CtElement element : body.getElements(e -> true)) {
                    if (element instanceof CtLocalVariable<?>) {
                        rewriteLocalVariable((CtLocalVariable<?>) element, flywayType);
                    } else if (element instanceof CtAssignment<?, ?>) {
                        rewriteAssignment((CtAssignment<?, ?>) element, flywayType);
                    }
                }
            }
        }
    }

    private static void rewriteLocalVariable(CtLocalVariable<?> variable, CtTypeReference<?> flywayType) {
        CtExpression<?> defaultExpression = variable.getDefaultExpression();
        if (!(defaultExpression instanceof CtConstructorCall<?>)) {
            return;
        }

        CtConstructorCall<?> call = (CtConstructorCall<?>) defaultExpression;
        if (!isOldFlywayConstruction(call, flywayType)) {
            return;
        }

        variable.setDefaultExpression(variable.getFactory().Code().createCodeSnippetExpression(buildReplacement(call)));
    }

    private static void rewriteAssignment(CtAssignment<?, ?> assignment, CtTypeReference<?> flywayType) {
        CtExpression<?> assigned = assignment.getAssignment();
        if (!(assigned instanceof CtConstructorCall<?>)) {
            return;
        }

        CtConstructorCall<?> call = (CtConstructorCall<?>) assigned;
        if (!isOldFlywayConstruction(call, flywayType)) {
            return;
        }

        assignment.setAssignment(assignment.getFactory().Code().createCodeSnippetExpression(buildReplacement(call)));
    }

    private static boolean isOldFlywayConstruction(CtConstructorCall<?> call, CtTypeReference<?> flywayType) {
        CtExecutableReference<?> executable = call.getExecutable();
        return executable != null
                && executable.getDeclaringType() != null
                && flywayType.getQualifiedName().equals(executable.getDeclaringType().getQualifiedName());
    }

    private static String buildReplacement(CtConstructorCall<?> call) {
        StringBuilder builder = new StringBuilder();
        builder.append("org.flywaydb.core.Flyway.configure()");

        CtExpression<?> dataSource = findArgument(call, "javax.sql.DataSource");
        CtExpression<?> classLoader = findArgument(call, "java.lang.ClassLoader");
        CtExpression<?> locations = findLocationsArgument(call);
        CtExpression<?> validate = findArgument(call, "boolean");

        if (classLoader != null) {
            builder.append(".loadDefaultConfigurationFiles(").append(classLoader).append(")");
        }
        if (dataSource != null) {
            builder.append(".dataSource(").append(dataSource).append(")");
        }
        if (locations != null) {
            builder.append(".locations(").append(locations).append(")");
        }
        if (validate != null) {
            builder.append(".validateOnMigrate(").append(validate).append(")");
        }
        builder.append(".load()");
        return builder.toString();
    }

    private static CtExpression<?> findArgument(CtConstructorCall<?> call, String typeName) {
        for (CtExpression<?> argument : call.getArguments()) {
            if (argument != null && argument.getType() != null && typeName.equals(argument.getType().getQualifiedName())) {
                return argument;
            }
        }
        return null;
    }

    private static CtExpression<?> findLocationsArgument(CtConstructorCall<?> call) {
        for (CtExpression<?> argument : call.getArguments()) {
            if (argument != null && argument.getType() != null) {
                String qn = argument.getType().getQualifiedName();
                if (qn.equals("java.lang.String") || qn.equals("java.lang.String[]")) {
                    return argument;
                }
            }
        }
        return null;
    }
}
