package github.chains;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtStatement;

public class Main {
    private static final String REMOVED_METHOD = "enableLogging";

    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: Main <input-source-dir> [output-dir]");
            System.exit(1);
        }

        String inputDir = args[0];
        String outputDir = args.length > 1 ? args[1] : inputDir;

        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.addInputResource(inputDir);
        launcher.setSourceOutputDirectory(outputDir);
        launcher.addProcessor(new AbstractProcessor<CtInvocation<?>>() {
            @Override
            public void process(CtInvocation<?> invocation) {
                if (!REMOVED_METHOD.equals(invocation.getExecutable().getSimpleName())) {
                    return;
                }
                if (invocation.getArguments().size() != 1) {
                    return;
                }

                CtStatement statement = invocation.getParent(CtStatement.class);
                if (statement != null) {
                    statement.delete();
                }
            }
        });
        launcher.run();
    }
}
