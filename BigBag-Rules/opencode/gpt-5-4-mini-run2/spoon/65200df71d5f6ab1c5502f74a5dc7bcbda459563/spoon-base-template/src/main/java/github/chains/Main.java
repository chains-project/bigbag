package github.chains;

import java.io.File;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtInvocation;

public class Main {
    private static final String OLD_DECLARING_TYPE =
            "org.sonarsource.sonarlint.core.analysis.api.AnalysisEngineConfiguration.Builder";
    private static final String OLD_METHOD_NAME = "addEnabledLanguages";

    public static void main(String[] args) {
        if (args.length < 2) {
            throw new IllegalArgumentException("Expected input and output source directories");
        }

        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(false);
        launcher.getEnvironment().setComplianceLevel(11);
        launcher.addInputResource(args[0]);
        launcher.setSourceOutputDirectory(new File(args[1]));
        launcher.addProcessor(new RemoveDeprecatedEnabledLanguagesCall());
        launcher.run();
    }

    private static final class RemoveDeprecatedEnabledLanguagesCall
            extends AbstractProcessor<CtInvocation<?>> {
        @Override
        public boolean isToBeProcessed(CtInvocation<?> candidate) {
            return candidate.getExecutable() != null
                    && candidate.getExecutable().getDeclaringType() != null
                    && OLD_DECLARING_TYPE.equals(
                            candidate.getExecutable().getDeclaringType().getQualifiedName())
                    && OLD_METHOD_NAME.equals(candidate.getExecutable().getSimpleName());
        }

        @Override
        public void process(CtInvocation<?> invocation) {
            if (invocation.getTarget() != null) {
                invocation.replace(invocation.getTarget().clone());
            } else {
                invocation.delete();
            }
        }
    }
}
