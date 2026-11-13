package github.chains;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import spoon.MavenLauncher;
import spoon.MavenLauncher.SOURCE_TYPE;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtExpression;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtTypeReference;

public final class Main {

    private static final Set<String> REMOVED_BOOLEAN_CONSTRUCTOR_TARGETS = new HashSet<>(Arrays.asList(
        "org.hamcrest.core.StringContains",
        "org.hamcrest.core.StringStartsWith"
    ));

    private Main() {
        // utility class
    }

    public static void main(final String[] args) {
        if (args.length < 1 || args.length > 2) {
            throw new IllegalArgumentException("Usage: Main <source-dir> [output-dir]");
        }
        final Path source = Paths.get(args[0]).toAbsolutePath().normalize();
        final Path output = args.length == 2
            ? Paths.get(args[1]).toAbsolutePath().normalize()
            : source;
        final MavenLauncher launcher = new MavenLauncher(source.toString(), SOURCE_TYPE.ALL_SOURCE);
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(false);
        launcher.getEnvironment().setComplianceLevel(17);
        launcher.setSourceOutputDirectory(output.toFile());
        launcher.addProcessor(new RemoveLeadingBooleanMatcherConstructorArgument());
        launcher.run();
    }

    private static final class RemoveLeadingBooleanMatcherConstructorArgument
        extends AbstractProcessor<CtConstructorCall<?>> {

        @Override
        public void process(final CtConstructorCall<?> call) {
            final CtExecutableReference<?> executable = call.getExecutable();
            if (executable == null) {
                return;
            }
            final CtTypeReference<?> declaring = executable.getDeclaringType();
            if (declaring == null || !REMOVED_BOOLEAN_CONSTRUCTOR_TARGETS.contains(
                declaring.getQualifiedName()
            )) {
                return;
            }
            final List<CtExpression<?>> arguments = call.getArguments();
            if (arguments.size() != 2 || !isBooleanExpression(arguments.get(0))) {
                return;
            }
            call.setArguments(List.of(arguments.get(1).clone()));
        }

        private boolean isBooleanExpression(final CtExpression<?> expression) {
            final CtTypeReference<?> type = expression.getType();
            return type != null && "boolean".equals(type.getSimpleName());
        }
    }
}
