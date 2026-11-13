package github.chains;

import java.nio.file.Path;
import java.nio.file.Paths;
import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.reference.CtTypeReference;

public final class Main {

    private static final String METHOD_NAME = "addEnabledLanguages";
    private static final String DECLARING_TYPE =
            "org.sonarsource.sonarlint.core.analysis.api.AnalysisEngineConfiguration.Builder";

    private Main() {}

    public static void main(String[] args) {
        if (args.length < 1) {
            throw new IllegalArgumentException("Usage: Main <input-source-dir> [output-dir]");
        }

        Path inputDir = Paths.get(args[0]);
        Path outputDir = args.length > 1 ? Paths.get(args[1]) : inputDir;

        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(true);
        launcher.getEnvironment().setComplianceLevel(11);
        launcher.addInputResource(inputDir.toString());
        launcher.setSourceOutputDirectory(outputDir.toString());
        launcher.addProcessor(new EnabledLanguagesCollectionToArrayProcessor());
        launcher.run();
    }

    private static final class EnabledLanguagesCollectionToArrayProcessor
            extends AbstractProcessor<CtInvocation<?>> {

        @Override
        public boolean isToBeProcessed(CtInvocation<?> invocation) {
            if (invocation.getExecutable() == null || invocation.getTarget() == null) {
                return false;
            }
            if (!METHOD_NAME.equals(invocation.getExecutable().getSimpleName())) {
                return false;
            }
            CtTypeReference<?> declaringType = invocation.getTarget().getType();
            return declaringType != null && DECLARING_TYPE.equals(declaringType.getQualifiedName());
        }

        @Override
        public void process(CtInvocation<?> invocation) {
            invocation.replace(invocation.getTarget());
        }
    }
}
