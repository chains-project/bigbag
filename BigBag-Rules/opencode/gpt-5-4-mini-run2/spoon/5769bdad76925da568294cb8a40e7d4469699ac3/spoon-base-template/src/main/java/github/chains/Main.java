package github.chains;

import java.io.File;
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

    public static void main(String[] args) {
        if (args.length < 1 || args.length > 2) {
            throw new IllegalArgumentException("Usage: Main <input-source-dir> [output-source-dir]");
        }

        File inputDir = new File(args[0]);
        File outputDir = args.length == 2 ? new File(args[1]) : new File(inputDir, "spoon-output");

        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(true);
        launcher.getEnvironment().setSourceOutputDirectory(outputDir);
        launcher.addInputResource(inputDir.getPath());
        launcher.addProcessor(new GhCompareStatusAccessProcessor());
        launcher.run();
    }

    static final class GhCompareStatusAccessProcessor extends AbstractProcessor<CtFieldAccess<?>> {
        @Override
        public void process(CtFieldAccess<?> access) {
            if (!isTargetStatusField(access)) {
                return;
            }

            CtExpression<?> target = access.getTarget();
            if (target == null) {
                return;
            }

            CtInvocation<?> replacement = buildGetterInvocation(access, target);
            access.replace(replacement);
        }

        private boolean isTargetStatusField(CtFieldAccess<?> access) {
            if (access.getVariable() == null || access.getTarget() == null) {
                return false;
            }

            CtTypeReference<?> declaringType = access.getVariable().getDeclaringType();
            return access.getVariable().getSimpleName().equals(OLD_FIELD_NAME)
                    && declaringType != null
                    && OLD_DECLARING_TYPE.equals(declaringType.getQualifiedName());
        }

        private CtInvocation<?> buildGetterInvocation(CtFieldAccess<?> access, CtExpression<?> target) {
            CtTypeReference<?> declaringType = access.getFactory().createReference(OLD_DECLARING_TYPE);
            CtExecutableReference<Object> getter = access.getFactory().createExecutableReference();
            getter.setSimpleName(NEW_METHOD_NAME);
            getter.setDeclaringType(declaringType);
            getter.setType(access.getFactory().createReference("org.kohsuke.github.GHCompare.Status"));

            return access.getFactory().Code().createInvocation(target.clone(), getter, Collections.emptyList());
        }
    }
}
