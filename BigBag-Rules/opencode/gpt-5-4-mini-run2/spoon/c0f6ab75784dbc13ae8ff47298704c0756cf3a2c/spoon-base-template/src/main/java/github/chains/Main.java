package github.chains;

import java.nio.file.Path;
import java.nio.file.Paths;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.reference.CtTypeReference;

public class Main {
    public static void main(String[] args) {
        if (args.length == 0) {
            throw new IllegalArgumentException("Usage: Main <input-source-dir> [output-dir]");
        }

        Path input = Paths.get(args[0]);
        Path output = args.length > 1 ? Paths.get(args[1]) : input;

        Launcher launcher = new Launcher();
        launcher.addInputResource(input.toString());
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.setSourceOutputDirectory(output.toFile());

        launcher.addProcessor(new AddEnabledLanguagesSetToVarargsProcessor());
        launcher.run();
    }

    private static final class AddEnabledLanguagesSetToVarargsProcessor
            extends AbstractProcessor<CtInvocation<?>> {
        private static final String METHOD_NAME = "addEnabledLanguages";
        private static final String OWNER_TYPE =
                "org.sonarsource.sonarlint.core.analysis.api.AnalysisEngineConfiguration.Builder";

        @Override
        public void process(CtInvocation<?> invocation) {
            if (!METHOD_NAME.equals(invocation.getExecutable().getSimpleName())) {
                return;
            }
            CtTypeReference<?> declaringType = invocation.getExecutable().getDeclaringType();
            if (declaringType == null || !OWNER_TYPE.equals(declaringType.getQualifiedName())) {
                return;
            }

            if (invocation.getTarget() != null) {
                invocation.replace(invocation.getTarget());
            } else {
                invocation.delete();
            }
        }
    }
}
