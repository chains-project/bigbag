package github.chains;

import java.nio.file.Path;
import java.nio.file.Paths;
import spoon.Launcher;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtExpression;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

public final class Main {

    private static final String AUTH_TYPE = "com.artipie.http.auth.Authentication";

    private static final String TARGET_TYPE = "com.artipie.http.auth.AuthSlice";

    private static final String REPLACEMENT_TYPE = "com.artipie.http.auth.BasicAuthSlice";

    private Main() {
        // utility class
    }

    public static void main(final String[] args) {
        if (args.length < 2) {
            throw new IllegalArgumentException("Usage: Main <input-source-dir> <output-dir>");
        }
        final Path input = Paths.get(args[0]);
        final Path output = Paths.get(args[1]);
        final Launcher launcher = new Launcher();
        launcher.addInputResource(input.toString());
        launcher.setSourceOutputDirectory(output.toFile());
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.buildModel();
        applyConstructorCompatibilityRule(launcher);
        launcher.prettyprint();
    }

    private static void applyConstructorCompatibilityRule(final Launcher launcher) {
        final CtTypeReference<?> authType = launcher.getFactory().Type().createReference(AUTH_TYPE);
        final CtTypeReference<?> replacementType = launcher.getFactory().Type().createReference(REPLACEMENT_TYPE);
        for (final CtConstructorCall<?> call : launcher.getModel().getElements(new TypeFilter<>(CtConstructorCall.class))) {
            if (call.getType() == null
                || call.getArguments().size() != 3
                || !TARGET_TYPE.equals(call.getType().getQualifiedName())) {
                continue;
            }
            final CtExpression<?> auth = call.getArguments().get(1);
            if (auth.getType() == null || (!AUTH_TYPE.equals(auth.getType().getQualifiedName())
                && !auth.getType().isSubtypeOf(authType))) {
                continue;
            }
            call.setType(replacementType);
        }
    }
}
