package github.chains;

import java.util.Collections;
import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtFieldAccess;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtTypeReference;

public class Main {
    private static final String OLD_DECLARING_TYPE = "org.kohsuke.github.GHCompare";
    private static final String OLD_FIELD_NAME = "status";
    private static final String NEW_METHOD_NAME = "getStatus";
    private static final String NEW_RETURN_TYPE = "org.kohsuke.github.GHCompare.Status";

    public static void main(String[] args) {
        if (args.length < 1 || args.length > 2) {
            throw new IllegalArgumentException("Usage: Main <inputSourceDir> [outputDir]");
        }

        String inputDir = args[0];
        String outputDir = args.length == 2 ? args[1] : inputDir + "-fixed";

        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(true);
        launcher.addInputResource(inputDir);
        launcher.setSourceOutputDirectory(outputDir);
        launcher.addProcessor(new ReplaceStatusFieldAccess());
        launcher.buildModel();
        launcher.process();
        launcher.prettyprint();
    }

    private static final class ReplaceStatusFieldAccess extends AbstractProcessor<CtFieldAccess<?>> {
        @Override
        public void process(CtFieldAccess<?> fieldRead) {
            if (fieldRead.getVariable() == null || !OLD_FIELD_NAME.equals(fieldRead.getVariable().getSimpleName())) {
                return;
            }

            CtExpression<?> target = fieldRead.getTarget();
            if (target == null) {
                return;
            }

            CtTypeReference<?> declaringType = fieldRead.getVariable().getDeclaringType();
            if (declaringType != null && !matchesType(declaringType) && !fieldRead.toString().endsWith("." + OLD_FIELD_NAME)) {
                return;
            }

            CtExecutableReference<?> getter = fieldRead.getFactory().Executable().createReference(
                    fieldRead.getFactory().Type().createReference(OLD_DECLARING_TYPE),
                    fieldRead.getFactory().Type().createReference(NEW_RETURN_TYPE),
                    NEW_METHOD_NAME,
                    Collections.emptyList());

            CtInvocation<?> replacement = fieldRead.getFactory().Code().createInvocation(target.clone(), getter, Collections.emptyList());
            fieldRead.replace(replacement);
        }

        private boolean matchesType(CtTypeReference<?> type) {
            String qn = type.getQualifiedName();
            return OLD_DECLARING_TYPE.equals(qn) || "GHCompare".equals(type.getSimpleName());
        }
    }
}
