package github.chains;

import java.io.File;
import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.reference.CtTypeReference;

public final class Main {

    private Main() {
        // utility class
    }

    public static void main(final String[] args) {
        if (args.length != 1) {
            throw new IllegalArgumentException("Expected source directory path");
        }
        final Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(false);
        launcher.getEnvironment().setSourceOutputDirectory(new File(args[0] + "-transformed"));
        launcher.addInputResource(new File(args[0]).getAbsolutePath());
        launcher.addProcessor(new HexOfBytesProcessor());
        launcher.run();
    }

    private static final class HexOfBytesProcessor extends AbstractProcessor<CtInvocation<?>> {

        @Override
        public boolean isToBeProcessed(final CtInvocation<?> candidate) {
            return isTarget(candidate);
        }

        @Override
        public void process(final CtInvocation<?> invocation) {
            final CtConstructorCall<?> hexCtor = (CtConstructorCall<?>) invocation.getTarget();
            final CtConstructorCall<?> bytesCtor = (CtConstructorCall<?>) hexCtor.getArguments().get(0);
            final CtExpression<?> bytesExpr = bytesCtor.getArguments().get(0).clone();
            invocation.replace(
                getFactory().Code().createCodeSnippetExpression(
                    "java.util.HexFormat.of().formatHex(" + bytesExpr + ")"
                )
            );
        }

        private static boolean isTarget(final CtInvocation<?> invocation) {
            if (!"asString".equals(invocation.getExecutable().getSimpleName())) {
                return false;
            }
            final CtExpression<?> target = invocation.getTarget();
            if (!(target instanceof CtConstructorCall)) {
                return false;
            }
            final CtConstructorCall<?> hexCtor = (CtConstructorCall<?>) target;
            if (!hasType(hexCtor.getType(), "org.cactoos.text.HexOf")) {
                return false;
            }
            if (hexCtor.getArguments().size() != 1 || !(hexCtor.getArguments().get(0) instanceof CtConstructorCall)) {
                return false;
            }
            final CtConstructorCall<?> bytesCtor = (CtConstructorCall<?>) hexCtor.getArguments().get(0);
            return hasType(bytesCtor.getType(), "org.cactoos.io.BytesOf") && bytesCtor.getArguments().size() == 1;
        }

        private static boolean hasType(final CtTypeReference<?> type, final String name) {
            return type != null && name.equals(type.getQualifiedName());
        }
    }
}
