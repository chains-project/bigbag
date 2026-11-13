package github.chains;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.factory.Factory;

import java.io.File;

public class Main {
    private static final String OLD_OWNER = "org.apache.maven.surefire.api.testset.TestListResolver";
    private static final String OLD_METHOD = "getWildcard";
    private static final String NEW_PATTERN = "*.class";

    public static void main(String[] args) {
        if (args.length < 1) {
            throw new IllegalArgumentException("Usage: Main <input-source-dir> [output-dir]");
        }

        String inputDir = args[0];
        String outputDir = args.length > 1 ? args[1] : inputDir + "-transformed";

        Launcher launcher = new Launcher();
        launcher.addInputResource(inputDir);
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.setSourceOutputDirectory(new File(outputDir));
        launcher.addProcessor(new AbstractProcessor<CtInvocation<?>>() {
            @Override
            public void process(CtInvocation<?> invocation) {
                if (!OLD_METHOD.equals(invocation.getExecutable().getSimpleName())) {
                    return;
                }
                if (invocation.getExecutable().getDeclaringType() == null) {
                    return;
                }
                if (!OLD_OWNER.equals(invocation.getExecutable().getDeclaringType().getQualifiedName())) {
                    return;
                }
                if (!invocation.getArguments().isEmpty()) {
                    return;
                }

                Factory factory = invocation.getFactory();
                invocation.replace(factory.Code().createConstructorCall(
                        factory.Type().createReference(OLD_OWNER),
                        factory.Code().createLiteral(NEW_PATTERN)
                ));
            }
        });

        launcher.run();
    }
}
