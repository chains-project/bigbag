package github.chains;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtTypeReference;

public final class Main {

    private static final String CCTOAST_BYTES_OF = "org.cactoos.io.BytesOf";

    private static final String CCTOAST_HEX_OF = "org.cactoos.text.HexOf";

    private static final String CCTOAST_LIST_OF = "org.cactoos.list.ListOf";

    private Main() {
        // utility class
    }

    public static void main(final String[] args) {
        final Path root = Paths.get(args.length > 0 ? args[0] : ".").toAbsolutePath();
        final Path output = args.length > 1 ? Paths.get(args[1]).toAbsolutePath() : root;
        processSourceRoot(root.resolve("src/main/java"), output.resolve("src/main/java"));
        processSourceRoot(root.resolve("src/test/java"), output.resolve("src/test/java"));
    }

    private static void processSourceRoot(final Path input, final Path output) {
        if (!Files.isDirectory(input)) {
            return;
        }
        final Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setComplianceLevel(8);
        launcher.addInputResource(input.toString());
        launcher.setSourceOutputDirectory(output.toFile());
        final HexOfBytesOfProcessor hex = new HexOfBytesOfProcessor();
        final ListOfProcessor lists = new ListOfProcessor();
        launcher.addProcessor(hex);
        launcher.addProcessor(lists);
        launcher.buildModel();
        launcher.getModel().processWith(hex);
        launcher.getModel().processWith(lists);
        launcher.prettyprint();
    }

    private static boolean matches(final CtTypeReference<?> type, final String fqn) {
        return type != null && fqn.equals(type.getQualifiedName());
    }

    private static final class HexOfBytesOfProcessor extends AbstractProcessor<CtInvocation<?>> {

        @Override
        public void process(final CtInvocation<?> invocation) {
            if (!"asString".equals(invocation.getExecutable().getSimpleName())) {
                return;
            }
            final CtExpression<?> target = invocation.getTarget();
            if (!(target instanceof CtConstructorCall)) {
                return;
            }
            final CtConstructorCall<?> hex = (CtConstructorCall<?>) target;
            if (!matches(hex.getType(), CCTOAST_HEX_OF) || hex.getArguments().size() != 1) {
                return;
            }
            final CtExpression<?> bytes = hex.getArguments().get(0);
            if (!(bytes instanceof CtConstructorCall)) {
                return;
            }
            final CtConstructorCall<?> wrapped = (CtConstructorCall<?>) bytes;
            if (!matches(wrapped.getType(), CCTOAST_BYTES_OF) || wrapped.getArguments().size() != 1) {
                return;
            }
            final String snippet = String.format(
                "org.apache.commons.codec.binary.Hex.encodeHexString(%s)",
                wrapped.getArguments().get(0)
            );
            invocation.replace(invocation.getFactory().Code().createCodeSnippetExpression(snippet));
        }
    }

    private static final class ListOfProcessor extends AbstractProcessor<CtConstructorCall<?>> {

        @Override
        public void process(final CtConstructorCall<?> call) {
            if (!matches(call.getType(), CCTOAST_LIST_OF)) {
                return;
            }
            if (call.getArguments().isEmpty()) {
                call.replace(call.getFactory().Code().createCodeSnippetExpression("java.util.Collections.emptyList()"));
                return;
            }
            if (call.getArguments().size() == 1) {
                return;
            }
            final StringBuilder snippet = new StringBuilder("java.util.Arrays.asList(");
            for (int idx = 0; idx < call.getArguments().size(); idx++) {
                if (idx > 0) {
                    snippet.append(", ");
                }
                snippet.append(call.getArguments().get(idx));
            }
            snippet.append(')');
            call.replace(call.getFactory().Code().createCodeSnippetExpression(snippet.toString()));
        }
    }
}
