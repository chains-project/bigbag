package github.chains;

import java.nio.file.Path;
import java.nio.file.Paths;
import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtInvocation;

public class Main {

    private static final String BROKEN_METHOD = "addEnabledLanguages";

    public static void main(String[] args) {
        if (args.length < 1) {
            throw new IllegalArgumentException(
                    "Expected at least one argument: <input-source-dir> [output-dir]");
        }

        Path inputDir = Paths.get(args[0]);
        Path outputDir =
                args.length > 1
                        ? Paths.get(args[1])
                        : inputDir.resolveSibling(inputDir.getFileName() + "-rewritten");

        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setIgnoreDuplicateDeclarations(true);
        launcher.addInputResource(inputDir.toString());
        launcher.setSourceOutputDirectory(outputDir.toFile());
        launcher.addProcessor(new RemoveBrokenInvocationProcessor());
        launcher.run();
    }

    private static final class RemoveBrokenInvocationProcessor extends AbstractProcessor<CtInvocation<?>> {

        @Override
        public void process(CtInvocation<?> invocation) {
            if (!BROKEN_METHOD.equals(invocation.getExecutable().getSimpleName())) {
                return;
            }

            CtInvocation<?> parentInvocation = invocation.getParent(CtInvocation.class);
            if (parentInvocation != null && parentInvocation.getTarget() == invocation) {
                parentInvocation.setTarget(invocation.getTarget());
            } else if (invocation.getTarget() != null) {
                invocation.replace(invocation.getTarget());
            } else {
                invocation.delete();
            }
        }
    }
}
