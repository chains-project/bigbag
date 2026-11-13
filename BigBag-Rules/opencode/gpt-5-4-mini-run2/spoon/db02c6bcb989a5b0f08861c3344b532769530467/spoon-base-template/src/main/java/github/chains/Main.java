package github.chains;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtExpression;

public final class Main {

    private static final Set<String> TARGET_TYPES = new HashSet<>(
        java.util.Arrays.asList(
            "org.hamcrest.core.StringContains",
            "org.hamcrest.core.StringStartsWith"
        )
    );

    private Main() {
        // utility class
    }

    public static void main(final String[] args) {
        final Path input = Paths.get(args.length > 0 ? args[0] : ".");
        final Path output = Paths.get(args.length > 1 ? args[1] : "spoon-out");

        final Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(true);
        launcher.addInputResource(input.toString());
        launcher.setSourceOutputDirectory(output.toFile());
        launcher.addProcessor(new IgnoreCaseConstructorFixer());
        launcher.run();
    }

    private static final class IgnoreCaseConstructorFixer extends AbstractProcessor<CtConstructorCall<?>> {

        @Override
        public void process(final CtConstructorCall<?> call) {
            final String type = call.getType() == null ? null : call.getType().getQualifiedName();
            if (!TARGET_TYPES.contains(type)) {
                return;
            }
            if (call.getArguments().size() != 2) {
                return;
            }
            final CtExpression<?> pattern = call.getArguments().get(1).clone();
            call.setArguments(Collections.singletonList(pattern));
        }
    }
}
