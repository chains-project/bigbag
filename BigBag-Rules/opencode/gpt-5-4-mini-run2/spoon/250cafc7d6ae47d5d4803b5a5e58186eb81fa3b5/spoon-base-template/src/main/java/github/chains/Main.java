package github.chains;

import spoon.Launcher;
import spoon.reflect.code.CtBlock;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtStatement;
import spoon.reflect.code.CtLocalVariable;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.ArrayList;
import java.util.List;

public class Main {
    private static final String FLYWAY_TYPE = "org.flywaydb.core.Flyway";

    public static void main(String[] args) {
        if (args.length < 2) {
            throw new IllegalArgumentException("Expected input and output source directories");
        }

        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(true);
        launcher.addInputResource(args[0]);
        launcher.setSourceOutputDirectory(args[1]);
        launcher.buildModel();

        for (Object element : new ArrayList<>(launcher.getModel().getElements(new TypeFilter<>(CtLocalVariable.class)))) {
            transformFlywayVariable((CtLocalVariable<?>) element);
        }

        launcher.prettyprint();
    }

    private static void transformFlywayVariable(CtLocalVariable<?> variable) {
        if (variable.getType() == null || !FLYWAY_TYPE.equals(variable.getType().getQualifiedName())) {
            return;
        }

        CtExpression<?> initializer = variable.getDefaultExpression();
        if (initializer == null || initializer.toString().contains("Flyway.configure")) {
            return;
        }

        String initializerText = initializer.toString();
        if (!initializerText.contains("new Flyway()") && !initializerText.contains("new org.flywaydb.core.Flyway()")) {
            return;
        }

        CtBlock<?> block = variable.getParent(CtBlock.class);
        if (block == null) {
            return;
        }

        List<CtStatement> statements = new ArrayList<>(block.getStatements());
        int declarationIndex = statements.indexOf(variable);
        if (declarationIndex < 0) {
            return;
        }

        String dataSourceCall = null;
        String classLoaderCall = null;
        String locationsCall = null;
        String validateCall = null;
        List<CtStatement> removable = new ArrayList<>();

        for (int i = declarationIndex + 1; i < statements.size(); i++) {
            CtStatement statement = statements.get(i);
            if (!(statement instanceof CtInvocation<?>)) {
                break;
            }

            CtInvocation<?> invocation = (CtInvocation<?>) statement;
            if (!isSetterOnVariable(invocation, variable.getSimpleName())) {
                break;
            }

            String setterName = invocation.getExecutable().getSimpleName();
            if ("setDataSource".equals(setterName) && !invocation.getArguments().isEmpty()) {
                dataSourceCall = invocation.getArguments().get(0).toString();
            } else if ("setClassLoader".equals(setterName) && !invocation.getArguments().isEmpty()) {
                classLoaderCall = invocation.getArguments().get(0).toString();
            } else if ("setLocations".equals(setterName) && !invocation.getArguments().isEmpty()) {
                List<String> args = new ArrayList<>();
                for (CtExpression<?> arg : invocation.getArguments()) {
                    args.add(arg.toString());
                }
                locationsCall = String.join(", ", args);
            } else if ("setValidateOnMigrate".equals(setterName) && !invocation.getArguments().isEmpty()) {
                validateCall = invocation.getArguments().get(0).toString();
            }
            removable.add(statement);
        }

        if (dataSourceCall == null) {
            return;
        }

        CtExpression<?> replacement = buildFluentFlywayExpression(classLoaderCall, dataSourceCall, locationsCall, validateCall);
        variable.setDefaultExpression((CtExpression) replacement);

        for (CtStatement statement : removable) {
            block.getStatements().remove(statement);
        }
    }

    private static CtExpression<?> buildFluentFlywayExpression(String classLoaderCall, String dataSourceCall, String locationsCall, String validateCall) {
        StringBuilder code = new StringBuilder();
        code.append("org.flywaydb.core.Flyway.configure(");
        if (classLoaderCall != null) {
            code.append(classLoaderCall);
        }
        code.append(")");
        code.append(".dataSource(").append(dataSourceCall).append(")");
        if (locationsCall != null) {
            code.append(".locations(").append(locationsCall).append(")");
        }
        if (validateCall != null) {
            code.append(".validateOnMigrate(").append(validateCall).append(")");
        }
        code.append(".load()");
        return new Launcher().getFactory().Code().createCodeSnippetExpression(code.toString());
    }

    private static boolean isSetterOnVariable(CtInvocation<?> invocation, String variableName) {
        if (invocation.getTarget() == null || invocation.getExecutable() == null) {
            return false;
        }
        if (!variableName.equals(invocation.getTarget().toString())) {
            return false;
        }
        String name = invocation.getExecutable().getSimpleName();
        return "setDataSource".equals(name) || "setLocations".equals(name) || "setValidateOnMigrate".equals(name) || "setClassLoader".equals(name);
    }
}
