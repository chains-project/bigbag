package github.chains;

import java.util.List;
import java.util.Collections;

import spoon.Launcher;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.visitor.filter.TypeFilter;

public class Main {
    public static void main(String[] args) {
        if (args.length < 2) {
            throw new IllegalArgumentException("Usage: <input-source-dir> <output-source-dir>");
        }

        final Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.addInputResource(args[0]);
        launcher.setSourceOutputDirectory(args[1]);

        launcher.buildModel();

        final List<CtInvocation<?>> invocations = launcher.getModel().getElements(new TypeFilter<>(CtInvocation.class));
        for (final CtInvocation<?> invocation : invocations) {
            if (!"setLineWidth".equals(invocation.getExecutable().getSimpleName())) {
                continue;
            }

            if (invocation.getArguments().size() != 1) {
                continue;
            }

            final CtExpression<?> oldArgument = invocation.getArguments().get(0);
            final CtExpression<?> newArgument = launcher.getFactory().Code()
                .createCodeSnippetExpression("Float.valueOf(" + oldArgument + ")");
            invocation.setArguments(Collections.singletonList(newArgument));
        }

        launcher.prettyprint();
    }

}
