package github.chains;

import java.nio.file.Path;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import spoon.Launcher;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtLiteral;
import spoon.reflect.visitor.filter.TypeFilter;

public final class Main {

    private static final Set<String> TARGET_TYPES = new HashSet<>(Arrays.asList(
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
        final Path input = Paths.get(args[0]);
        final Path output = Paths.get(args.length == 2 ? args[1] : args[0]);
        final Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setIgnoreDuplicateDeclarations(true);
        try {
            Files.walk(input)
                .filter(path -> path.toString().endsWith(".java"))
                .filter(path -> !path.getFileName().toString().equals("package-info.java"))
                .forEach(path -> launcher.addInputResource(path.toString()));
        } catch (final java.io.IOException err) {
            throw new IllegalStateException(err);
        }
        launcher.setSourceOutputDirectory(output.toFile());
        launcher.buildModel();
        launcher.getModel().getElements(new TypeFilter<>(CtConstructorCall.class)).forEach(call -> {
            final CtConstructorCall<?> ctor = (CtConstructorCall<?>) call;
            final String qualifiedName = ctor.getType() == null ? null : ctor.getType().getQualifiedName();
            final String simpleName = ctor.getType() == null ? null : ctor.getType().getSimpleName();
            final boolean target = TARGET_TYPES.contains(qualifiedName)
                || TARGET_TYPES.stream().map(name -> name.substring(name.lastIndexOf('.') + 1))
                    .anyMatch(simpleName::equals);
            if (target && ctor.getArguments().size() == 2 && isBooleanPrefix(ctor.getArguments().get(0))) {
                final List<CtExpression<?>> argsList = ctor.getArguments();
                argsList.remove(0);
            }
        });
        launcher.prettyprint();
    }

    private static boolean isBooleanPrefix(final CtExpression<?> expr) {
        return expr instanceof CtLiteral
            && ((CtLiteral<?>) expr).getValue() instanceof Boolean;
    }
}
