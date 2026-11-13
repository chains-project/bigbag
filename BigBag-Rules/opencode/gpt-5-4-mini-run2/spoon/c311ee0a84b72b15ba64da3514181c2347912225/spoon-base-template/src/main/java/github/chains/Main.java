package github.chains;

import java.io.File;
import java.util.List;
import java.util.Set;
import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtExpression;

/**
 * Generic Spoon transform for Hamcrest string matchers.
 */
public final class Main {

    private static final Set<String> TARGET_TYPES = Set.of(
        "org.hamcrest.core.StringContains",
        "org.hamcrest.core.StringStartsWith"
    );

    private Main() {
        // Utility class.
    }

    public static void main(final String[] args) {
        if (args.length < 1 || args.length > 2) {
            throw new IllegalArgumentException("Usage: Main <input-source-dir> [output-source-dir]");
        }
        final File input = new File(args[0]);
        final File output = new File(args.length == 2 ? args[1] : args[0]);
        final Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(true);
        launcher.setSourceOutputDirectory(output.getAbsolutePath());
        launcher.addInputResource(input.getAbsolutePath());
        launcher.addProcessor(new AbstractProcessor<CtConstructorCall<?>>() {
            @Override
            public void process(final CtConstructorCall<?> call) {
                if (!isTarget(call) || call.getArguments().size() != 2) {
                    return;
                }
                final List<CtExpression<?>> arguments = call.getArguments();
                call.setArguments(List.of(arguments.get(1)));
            }
        });
        launcher.run();
    }

    private static boolean isTarget(final CtConstructorCall<?> call) {
        return call.getExecutable() != null
            && call.getExecutable().getDeclaringType() != null
            && TARGET_TYPES.contains(call.getExecutable().getDeclaringType().getQualifiedName());
    }
}
