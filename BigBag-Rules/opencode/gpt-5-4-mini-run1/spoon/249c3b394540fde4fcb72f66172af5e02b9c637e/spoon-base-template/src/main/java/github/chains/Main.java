package github.chains;

import spoon.Launcher;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtLocalVariable;
import spoon.reflect.code.CtStatement;
import spoon.reflect.visitor.filter.TypeFilter;

public class Main {
    private static final String DUMPER_OPTIONS = "org.yaml.snakeyaml.DumperOptions";

    public static void main(String[] args) {
        if (args.length == 0) {
            throw new IllegalArgumentException("Expected source directory path");
        }

        Launcher launcher = new Launcher();
        launcher.addInputResource(args[0]);
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.buildModel();

        for (CtLocalVariable<?> variable : launcher.getModel().getElements(new TypeFilter<>(CtLocalVariable.class))) {
            if (DUMPER_OPTIONS.equals(variable.getType().getQualifiedName())
                    && variable.getDefaultExpression() instanceof CtConstructorCall) {
                CtStatement scalarFix = launcher.getFactory().Code().createCodeSnippetStatement(
                        variable.getSimpleName() + ".setDefaultScalarStyle(org.yaml.snakeyaml.DumperOptions.ScalarStyle.PLAIN);");
                CtStatement flowFix = launcher.getFactory().Code().createCodeSnippetStatement(
                        variable.getSimpleName() + ".setDefaultFlowStyle(org.yaml.snakeyaml.DumperOptions.FlowStyle.AUTO);");
                variable.insertAfter(scalarFix);
                scalarFix.insertAfter(flowFix);
            }
        }

        launcher.prettyprint();
    }
}
