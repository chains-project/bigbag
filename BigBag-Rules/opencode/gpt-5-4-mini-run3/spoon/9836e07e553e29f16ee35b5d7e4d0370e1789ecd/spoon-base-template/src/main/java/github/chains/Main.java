package github.chains;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import spoon.Launcher;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

public class Main {

    private static final Set<String> TARGET_TYPES = Set.of(
        "org.hamcrest.core.StringContains",
        "org.hamcrest.core.StringStartsWith",
        "org.hamcrest.core.StringEndsWith"
    );

    private static final Set<String> TARGET_SIMPLE_NAMES = Set.of(
        "StringContains",
        "StringStartsWith",
        "StringEndsWith"
    );

    public static void main(final String[] args) throws Exception {
        if (args.length < 2) {
            throw new IllegalArgumentException("Usage: <input-source-dir> <output-dir>");
        }
        final Path input = Path.of(args[0]).toAbsolutePath().normalize();
        final Path output = Path.of(args[1]).toAbsolutePath().normalize();
        if (!Files.exists(input)) {
            throw new IllegalArgumentException("Input path does not exist: " + input);
        }
        final Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(true);
        launcher.setSourceOutputDirectory(output.toFile());
        launcher.addInputResource(input.toString());
        launcher.buildModel();
        rewriteOldSubstringMatchers(launcher);
        launcher.prettyprint();
    }

    private static void rewriteOldSubstringMatchers(final Launcher launcher) {
        final List<CtConstructorCall<?>> calls = launcher.getModel().getElements(
            new TypeFilter<>(CtConstructorCall.class)
        );
        for (final CtConstructorCall<?> call : calls) {
            if (isOldSubstringMatcherConstructor(call) && call.getArguments().size() == 2) {
                call.setArguments(List.of(call.getArguments().get(1)));
            }
        }
    }

    private static boolean isOldSubstringMatcherConstructor(final CtConstructorCall<?> call) {
        final CtTypeReference<?> type = call.getType();
        if (type == null) {
            return false;
        }
        return TARGET_TYPES.contains(type.getQualifiedName()) || TARGET_SIMPLE_NAMES.contains(type.getSimpleName());
    }
}
