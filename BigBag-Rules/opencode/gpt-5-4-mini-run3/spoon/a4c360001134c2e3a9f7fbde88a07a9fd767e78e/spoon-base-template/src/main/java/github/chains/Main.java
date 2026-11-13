package github.chains;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtTypeReference;

public class Main {
    private static final String OLD_DECLARING_TYPE = "org.reflections.util.FilterBuilder";
    private static final String OLD_METHOD_NAME = "apply";
    private static final String NEW_METHOD_NAME = "test";

    public static void main(String[] args) {
        if (args.length < 1 || args.length > 2) {
            throw new IllegalArgumentException("Usage: Main <input-source-dir> [output-source-dir]");
        }

        String inputSourceDir = args[0];
        String outputSourceDir = args.length == 2 ? args[1] : inputSourceDir;

        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.addInputResource(inputSourceDir);
        launcher.setSourceOutputDirectory(outputSourceDir);
        launcher.addProcessor(new AbstractProcessor<CtElement>() {
            @Override
            public void process(CtElement element) {
                if (!(element instanceof CtInvocation<?> invocation)) {
                    return;
                }

                CtExecutableReference<?> executable = invocation.getExecutable();
                if (!OLD_METHOD_NAME.equals(executable.getSimpleName())) {
                    return;
                }

                if (!matchesOldFilterBuilderApi(invocation, executable)) {
                    return;
                }

                executable.setSimpleName(NEW_METHOD_NAME);
            }

            private boolean matchesOldFilterBuilderApi(CtInvocation<?> invocation, CtExecutableReference<?> executable) {
                CtTypeReference<?> declaringType = executable.getDeclaringType();
                if (declaringType != null && OLD_DECLARING_TYPE.equals(declaringType.getQualifiedName())) {
                    return true;
                }

                if (invocation.getTarget() instanceof CtInvocation<?> targetInvocation) {
                    if ("include".equals(targetInvocation.getExecutable().getSimpleName())) {
                        CtTypeReference<?> targetType = targetInvocation.getTarget() == null ? null : targetInvocation.getTarget().getType();
                        if (targetType != null && OLD_DECLARING_TYPE.equals(targetType.getQualifiedName())) {
                            return true;
                        }
                    }
                }

                if (invocation.getTarget() instanceof CtConstructorCall<?> constructorCall) {
                    CtTypeReference<?> type = constructorCall.getType();
                    if (type != null && OLD_DECLARING_TYPE.equals(type.getQualifiedName())) {
                        return true;
                    }
                }

                return false;
            }
        });
        launcher.run();
    }
}
