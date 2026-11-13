package github.chains;

import java.nio.file.Path;
import java.nio.file.Paths;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.reference.CtTypeReference;

public class Main {
    private static final String TARGET_METHOD = "addEnabledLanguages";
    private static final String TARGET_DECLARING_TYPE =
            "org.sonarsource.sonarlint.core.analysis.api.AnalysisEngineConfiguration.Builder";

    public static void main(String[] args) {
        if (args.length < 2) {
            throw new IllegalArgumentException("Usage: Main <input-source-dir> <output-source-dir>");
        }

        Path inputDir = Paths.get(args[0]);
        Path outputDir = Paths.get(args[1]);

        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.addInputResource(inputDir.toString());
        launcher.setSourceOutputDirectory(outputDir.toFile());
        launcher.addProcessor(new AddEnabledLanguagesProcessor());
        launcher.run();
    }

    private static final class AddEnabledLanguagesProcessor extends AbstractProcessor<CtInvocation<?>> {
        @Override
        public void process(CtInvocation<?> invocation) {
            if (!TARGET_METHOD.equals(invocation.getExecutable().getSimpleName())) {
                return;
            }

            CtTypeReference<?> declaringType = invocation.getExecutable().getDeclaringType();
            if (declaringType == null || !TARGET_DECLARING_TYPE.equals(declaringType.getQualifiedName())) {
                return;
            }

            if (invocation.getArguments().size() != 1) {
                return;
            }

            CtExpression<?> target = invocation.getTarget();
            if (target != null) {
                invocation.replace(target.clone());
            } else {
                invocation.delete();
            }
        }
    }
}
