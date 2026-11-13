package github.chains;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.io.File;
import java.util.Arrays;
import java.util.Set;
import java.util.HashSet;
import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtExpression;
import spoon.reflect.reference.CtTypeReference;

public final class Main {

    private static final Set<String> TARGETS = new HashSet<>(Set.of(
        "org.hamcrest.core.StringContains",
        "org.hamcrest.core.StringStartsWith"
    ));

    private Main() {
    }

    public static void main(final String[] args) {
        if (args.length < 1 || args.length > 2) {
            throw new IllegalArgumentException("Usage: Main <input-dir> [output-dir]");
        }
        final Path input = Paths.get(args[0]);
        final Path output = args.length == 2 ? Paths.get(args[1]) : input;
        final Launcher launcher = new Launcher();
        launcher.addInputResource(input.toString());
        launcher.setSourceOutputDirectory(output.toFile());
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(false);
        launcher.getEnvironment().setCommentEnabled(false);
        launcher.addProcessor(new StringMatcherProcessor());
        launcher.run();
    }

    private static final class StringMatcherProcessor extends AbstractProcessor<CtConstructorCall<?>> {

        @Override
        public boolean isToBeProcessed(final CtConstructorCall<?> call) {
            return call.getType() != null
                && TARGETS.contains(call.getType().getQualifiedName())
                && call.getArguments().size() == 2
                && call.getArguments().get(0) instanceof CtExpression
                && call.getArguments().get(1) instanceof CtExpression;
        }

        @Override
        public void process(final CtConstructorCall<?> call) {
            final CtExpression<?> text = call.getArguments().get(1);
            final CtTypeReference<?> type = call.getType();
            final CtTypeReference<?> target = call.getFactory().Type().createReference(type.getQualifiedName());
            call.replace(call.getFactory().Code().createConstructorCall(target, text));
        }
    }
}
