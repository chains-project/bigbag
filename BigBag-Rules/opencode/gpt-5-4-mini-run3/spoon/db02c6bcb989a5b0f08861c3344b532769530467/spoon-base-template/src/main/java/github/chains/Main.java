package github.chains;

import java.io.File;
import java.util.Collections;
import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtLiteral;

public final class Main {

    private static final String TARGET_PACKAGE = "org.hamcrest.core.";

    private Main() {
    }

    public static void main(final String[] args) {
        if (args.length < 1) {
            throw new IllegalArgumentException("Expected source directory path");
        }
        final File base = new File(args[0]);
        final Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(false);
        launcher.getEnvironment().setIgnoreDuplicateDeclarations(true);
        final File main = new File(base, "src/main/java");
        final File test = new File(base, "src/test/java");
        if (main.isDirectory()) {
            launcher.addInputResource(main.getAbsolutePath());
        }
        if (test.isDirectory()) {
            launcher.addInputResource(test.getAbsolutePath());
        }
        if (!main.isDirectory() && !test.isDirectory()) {
            launcher.addInputResource(base.getAbsolutePath());
        }
        final String output = args.length > 1 ? args[1] : new File(base, "target/spoon-out").getAbsolutePath();
        launcher.getEnvironment().setSourceOutputDirectory(new File(output));
        launcher.addProcessor(new DeprecatedBooleanCtorRemoval());
        launcher.run();
    }

    private static final class DeprecatedBooleanCtorRemoval
        extends AbstractProcessor<CtConstructorCall<?>> {

        @Override
        public void process(final CtConstructorCall<?> call) {
            if (!isTarget(call) || call.getArguments().size() != 2) {
                return;
            }
            final CtExpression<?> first = call.getArguments().get(0);
            if (!(first instanceof CtLiteral<?> literal) || !(literal.getValue() instanceof Boolean)) {
                return;
            }
            call.setArguments(Collections.singletonList(call.getArguments().get(1)));
        }

        private boolean isTarget(final CtConstructorCall<?> call) {
            return call.getType() != null
                && call.getType().getQualifiedName().startsWith(TARGET_PACKAGE);
        }
    }
}
