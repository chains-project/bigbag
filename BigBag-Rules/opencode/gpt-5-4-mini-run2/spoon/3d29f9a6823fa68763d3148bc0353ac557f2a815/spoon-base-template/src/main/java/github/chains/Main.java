package github.chains;

import java.nio.file.Path;
import java.nio.file.Paths;
import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;

public class Main {
    private static final String METHOD_NAME = "addEnabledLanguages";
    public static void main(String[] args) {
        if (args.length < 1 || args.length > 2) {
            System.err.println("Usage: Main <input-source-dir> [output-source-dir]");
            System.exit(1);
        }

        Path inputDir = Paths.get(args[0]);
        Path outputDir = args.length == 2 ? Paths.get(args[1]) : inputDir.resolveSibling(inputDir.getFileName() + "-fixed");

        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setSourceOutputDirectory(outputDir.toFile());
        launcher.addInputResource(inputDir.toString());
        launcher.addProcessor(new AddEnabledLanguagesCollectionToVarargsProcessor());
        launcher.run();
    }

    private static class AddEnabledLanguagesCollectionToVarargsProcessor
            extends AbstractProcessor<CtInvocation<?>> {

        @Override
        public void process(CtInvocation<?> invocation) {
            if (!METHOD_NAME.equals(invocation.getExecutable().getSimpleName())) {
                return;
            }
            if (invocation.getArguments().size() != 1) {
                return;
            }
            CtExpression<?> target = invocation.getTarget();
            if (target != null) {
                CtExpression<?> replacement =
                        getFactory().Code().createCodeSnippetExpression(target.prettyprint());
                invocation.replace(replacement);
            }
        }
    }
}
