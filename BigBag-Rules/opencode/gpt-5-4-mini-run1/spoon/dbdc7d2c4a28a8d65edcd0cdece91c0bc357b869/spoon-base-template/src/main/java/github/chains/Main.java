package github.chains;

import java.io.File;

import spoon.Launcher;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

public class Main {
    private static final String REMOVED_METHOD = "enableLogging";
    private static final String REMOVED_OWNER = "org.codehaus.plexus.archiver.AbstractUnArchiver";

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            throw new IllegalArgumentException("Usage: Main <sourceDir> [outputDir]");
        }

        File sourceDir = new File(args[0]);
        File outputDir = args.length > 1 ? new File(args[1]) : sourceDir;

        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(false);
        launcher.setSourceOutputDirectory(outputDir);

        launcher.addInputResource(sourceDir.getAbsolutePath());
        launcher.buildModel();

        for (CtInvocation<?> invocation : launcher.getModel().getElements(new TypeFilter<>(CtInvocation.class))) {
            if (isRemovedLoggingCall(invocation)) {
                deleteEnclosingStatement(invocation);
            }
        }

        launcher.prettyprint();
    }

    private static boolean isRemovedLoggingCall(CtInvocation<?> invocation) {
        if (!REMOVED_METHOD.equals(invocation.getExecutable().getSimpleName())) {
            return false;
        }
        if (invocation.getArguments().size() != 1) {
            return false;
        }

        CtExpression<?> target = invocation.getTarget();
        if (target == null) {
            return false;
        }

        CtTypeReference<?> targetType = target.getType();
        if (targetType == null) {
            return false;
        }

        CtTypeReference<?> removedOwner = invocation.getFactory().Type().createReference(REMOVED_OWNER);
        return targetType.isSubtypeOf(removedOwner) || REMOVED_OWNER.equals(targetType.getQualifiedName());
    }

    private static void deleteEnclosingStatement(CtInvocation<?> invocation) {
        if (invocation.getParent(CtStatement.class) != null) {
            invocation.getParent(CtStatement.class).replace(invocation.getFactory().Core().createBlock());
        } else {
            invocation.delete();
        }
    }
}
