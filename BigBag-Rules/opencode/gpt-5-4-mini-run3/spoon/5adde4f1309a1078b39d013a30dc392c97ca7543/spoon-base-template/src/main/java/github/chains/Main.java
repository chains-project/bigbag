package github.chains;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;

public class Main {
    private static final String TARGET_METHOD = "addEnabledLanguages";

    public static void main(String[] args) {
        if (args.length < 1 || args.length > 2) {
            throw new IllegalArgumentException("Usage: <input-source-dir> [output-dir]");
        }

        Path inputDir = Paths.get(args[0]).toAbsolutePath();
        Path outputDir = args.length == 2 ? Paths.get(args[1]).toAbsolutePath() : inputDir;

        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setComplianceLevel(17);
        launcher.addInputResource(inputDir.toString());
        launcher.setSourceOutputDirectory(outputDir.toFile());
        launcher.addProcessor(new AddEnabledLanguagesFixProcessor());
        launcher.run();
    }

    private static final class AddEnabledLanguagesFixProcessor
            extends AbstractProcessor<CtInvocation<?>> {

        @Override
        public void process(CtInvocation<?> invocation) {
            if (!TARGET_METHOD.equals(invocation.getExecutable().getSimpleName())) {
                return;
            }

            List<CtExpression<?>> arguments = invocation.getArguments();
            if (arguments.size() != 1) {
                return;
            }

            CtExpression<?> target = invocation.getTarget();
            if (target == null) {
                return;
            }

            invocation.replace(target.clone());
        }
    }
}
