package github.chains;

import java.nio.file.Path;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.reference.CtTypeReference;

public class Main {
    private static final String TARGET_API = "org.codehaus.plexus.archiver.AbstractUnArchiver";

    public static void main(String[] args) {
        if (args.length < 1) {
            throw new IllegalArgumentException("Usage: Main <input-source-dir> [output-source-dir]");
        }

        Path input = Path.of(args[0]).toAbsolutePath().normalize();
        Path output = args.length > 1
            ? Path.of(args[1]).toAbsolutePath().normalize()
            : input.resolveSibling(input.getFileName() + "-transformed");

        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(true);
        launcher.getEnvironment().setComplianceLevel(8);
        launcher.setSourceOutputDirectory(output.toString());
        launcher.addInputResource(input.toString());
        launcher.addProcessor(new RemoveEnableLoggingCalls());
        launcher.run();
    }

    private static final class RemoveEnableLoggingCalls extends AbstractProcessor<CtInvocation<?>> {
        @Override
        public void process(CtInvocation<?> invocation) {
            if (!isTargetInvocation(invocation)) {
                return;
            }
            invocation.delete();
        }

        private boolean isTargetInvocation(CtInvocation<?> invocation) {
            if (invocation.getExecutable() == null) {
                return false;
            }
            if (!"enableLogging".equals(invocation.getExecutable().getSimpleName())) {
                return false;
            }
            if (invocation.getArguments().size() != 1) {
                return false;
            }
            CtTypeReference<?> declaringType = invocation.getExecutable().getDeclaringType();
            if (declaringType == null) {
                return true;
            }
            if (TARGET_API.equals(declaringType.getQualifiedName())) {
                return true;
            }
            CtTypeReference<?> targetType = getFactory().Type().createReference(TARGET_API);
            return declaringType.isSubtypeOf(targetType);
        }
    }
}
