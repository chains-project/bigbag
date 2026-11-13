package github.chains;

import java.nio.file.Path;
import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.code.CtExpression;

public class Main {

    private static final String TARGET_DECLARING_TYPE =
            "org.sonarsource.sonarlint.core.analysis.api.AnalysisEngineConfiguration.Builder";
    private static final String TARGET_METHOD = "addEnabledLanguages";

    public static void main(String[] args) {
        if (args.length < 2) {
            throw new IllegalArgumentException("Expected input and output source directories");
        }

        Launcher launcher = new Launcher();
        launcher.addInputResource(args[0]);
        launcher.setSourceOutputDirectory(Path.of(args[1]).toFile());
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(true);
        launcher.addProcessor(new RemoveDeprecatedAnalysisEngineBuilderCall());
        launcher.run();
    }

    private static final class RemoveDeprecatedAnalysisEngineBuilderCall
            extends AbstractProcessor<CtInvocation<?>> {

        @Override
        public boolean isToBeProcessed(CtInvocation<?> candidate) {
            CtExecutableReference<?> executable = candidate.getExecutable();
            if (executable == null || !TARGET_METHOD.equals(executable.getSimpleName())) {
                return false;
            }
            CtTypeReference<?> declaringType = executable.getDeclaringType();
            return declaringType != null && TARGET_DECLARING_TYPE.equals(declaringType.getQualifiedName());
        }

        @Override
        public void process(CtInvocation<?> invocation) {
            CtExpression<?> target = invocation.getTarget();
            CtElement parent = invocation.getParent();

            if (parent instanceof CtInvocation) {
                ((CtInvocation<?>) parent).setTarget(target == null ? null : target.clone());
                invocation.delete();
                return;
            }

            if (target != null) {
                invocation.replace(target.clone());
            } else {
                invocation.delete();
            }
        }
    }
}
