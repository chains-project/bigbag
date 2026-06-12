package github.chains;

import spoon.Launcher;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.visitor.filter.TypeFilter;

public class Main {
    public static void main(String[] args) {
        if (args.length < 2) {
            throw new IllegalArgumentException("Expected input and output source directories");
        }

        Launcher launcher = new Launcher();
        launcher.addInputResource(args[0]);
        launcher.setSourceOutputDirectory(args[1]);
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.buildModel();

        String newTypeName = "org.codehaus.plexus.DefaultPlexusContainer";

        for (CtInvocation<?> invocation : launcher.getModel().getElements(new TypeFilter<>(CtInvocation.class))) {
            if (!"getLoggerManager".equals(invocation.getExecutable().getSimpleName())) {
                continue;
            }

            if (invocation.getTarget() == null || !invocation.getArguments().isEmpty()) {
                continue;
            }

            String castedTarget = "((" + newTypeName + ") (" + invocation.getTarget() + "))";
            invocation.setTarget(launcher.getFactory().Code().createCodeSnippetExpression(castedTarget));
        }

        launcher.prettyprint();
    }
}
