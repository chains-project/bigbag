package github.chains;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtExpression;
import spoon.reflect.reference.CtTypeReference;

import java.io.File;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class Main {
    private static final Set<String> LEGACY_CONTAINER_TYPES = new HashSet<>(Arrays.asList(
            "org.codehaus.plexus.PlexusContainer",
            "org.codehaus.plexus.MutablePlexusContainer"
    ));

    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: <input-source-dir> <output-source-dir>");
            System.exit(1);
        }

        Launcher launcher = new Launcher();
        launcher.addInputResource(args[0]);
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setComplianceLevel(17);
        launcher.setSourceOutputDirectory(new File(args[1]));

        launcher.addProcessor(new AbstractProcessor<CtInvocation<?>>() {
            @Override
            public void process(CtInvocation<?> invocation) {
                if (!isLegacyLoggerManagerCall(invocation)) {
                    return;
                }
                CtExpression<?> target = invocation.getTarget();
                if (target == null) {
                    return;
                }
                String replacement = "((org.codehaus.plexus.MutablePlexusContainer) "
                        + target.toString()
                        + ").getLoggerManager()";
                invocation.replace(invocation.getFactory().Code().createCodeSnippetExpression(replacement));
            }

            private boolean isLegacyLoggerManagerCall(CtInvocation<?> invocation) {
                if (invocation.getExecutable() == null) {
                    return false;
                }
                if (!"getLoggerManager".equals(invocation.getExecutable().getSimpleName())) {
                    return false;
                }
                if (!invocation.getArguments().isEmpty()) {
                    return false;
                }
                CtTypeReference<?> targetType = invocation.getTarget() == null ? null : invocation.getTarget().getType();
                if (targetType == null) {
                    return true;
                }
                String qualifiedName = targetType.getQualifiedName();
                return LEGACY_CONTAINER_TYPES.contains(qualifiedName);
            }
        });
        launcher.buildModel();
        launcher.process();
        launcher.prettyprint();
    }
}
