package github.chains;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtFieldRead;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtFieldReference;
import spoon.reflect.reference.CtTypeReference;

import java.util.Collections;

public class Main {
    private static final String OLD_TYPE = "org.kohsuke.github.GHCompare";
    private static final String OLD_FIELD = "status";
    private static final String NEW_TYPE = "org.kohsuke.github.GHCompare.Status";
    private static final String NEW_METHOD = "getStatus";

    public static void main(String[] args) {
        if (args.length < 1 || args.length > 2) {
            throw new IllegalArgumentException("Usage: Main <input-source-dir> [output-dir]");
        }

        String inputDir = args[0];
        String outputDir = args.length == 2 ? args[1] : inputDir + "-fixed";

        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setComplianceLevel(17);
        launcher.getEnvironment().setAutoImports(true);
        launcher.setSourceOutputDirectory(outputDir);
        launcher.addInputResource(inputDir);
        launcher.addProcessor(new StatusFieldAccessProcessor());
        launcher.run();
    }

    private static final class StatusFieldAccessProcessor extends AbstractProcessor<CtFieldRead<?>> {
        @Override
        public void process(CtFieldRead<?> fieldRead) {
            CtFieldReference<?> field = fieldRead.getVariable();
            if (field == null || !OLD_FIELD.equals(field.getSimpleName())) {
                return;
            }

            CtTypeReference<?> declaringType = field.getDeclaringType();
            if (declaringType == null || !OLD_TYPE.equals(declaringType.getQualifiedName())) {
                return;
            }

            CtExpression<?> target = fieldRead.getTarget();
            if (target == null) {
                return;
            }

            CtTypeReference<?> statusType = getFactory().Type().createReference(NEW_TYPE);
            CtExecutableReference<?> getter = getFactory().Executable().createReference(
                    getFactory().Type().createReference(OLD_TYPE),
                    statusType,
                    NEW_METHOD,
                    Collections.emptyList());
            CtInvocation<?> replacement = getFactory().Code().createInvocation(target.clone(), getter, Collections.emptyList());
            fieldRead.replace(replacement);
        }
    }
}
