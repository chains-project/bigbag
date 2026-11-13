package github.chains;

import spoon.Launcher;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.visitor.filter.TypeFilter;

public class Main {
    private static final String OLD_METHOD = "getArgumentAt";
    private static final String NEW_METHOD = "getArgument";
    private static final String OLD_OWNER = "org.mockito.invocation.InvocationOnMock";

    public static void main(String[] args) {
        if (args.length == 0) {
            throw new IllegalArgumentException("Usage: Main <input-source-dir> [output-source-dir]");
        }

        final String inputDir = args[0];
        final String outputDir = args.length > 1 ? args[1] : "target/spooned";

        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.addInputResource(inputDir);
        launcher.setSourceOutputDirectory(outputDir);
        launcher.buildModel();

        for (CtInvocation<?> invocation : launcher.getModel().getElements(new TypeFilter<>(CtInvocation.class))) {
            CtExecutableReference<?> executable = invocation.getExecutable();
            if (executable != null && executable.getDeclaringType() != null
                && OLD_METHOD.equals(executable.getSimpleName())
                && OLD_OWNER.equals(executable.getDeclaringType().getQualifiedName())
                && executable.getParameters().size() == 2) {
                CtExecutableReference updated = launcher.getFactory().createExecutableReference();
                updated.setSimpleName(NEW_METHOD);
                updated.setDeclaringType(executable.getDeclaringType());
                updated.setParameters(executable.getParameters());
                ((CtInvocation) invocation).setExecutable(updated);
            }
        }

        launcher.prettyprint();
    }
}
