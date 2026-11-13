package github.chains;

import java.nio.file.Path;
import java.nio.file.Paths;
import spoon.Launcher;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

public class Main {

    private static final String CONNECTION_FQN = "com.artipie.http.Connection";

    private static final String HEADERS_FROM_FQN = "com.artipie.http.Headers.From";

    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: Main <input-source-dir> <output-source-dir>");
            System.exit(1);
        }
        final Path input = Paths.get(args[0]);
        final Path output = Paths.get(args[1]);
        final Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.addInputResource(input.toString());
        launcher.setSourceOutputDirectory(output.toFile());
        launcher.buildModel();

        for (CtInvocation<?> invocation : launcher.getModel().getElements(new TypeFilter<>(CtInvocation.class))) {
            if (shouldRewrite(invocation)) {
                rewrite(invocation, launcher);
            }
        }

        launcher.prettyprint();
    }

    private static boolean shouldRewrite(final CtInvocation<?> invocation) {
        if (invocation.getArguments().size() != 3) {
            return false;
        }
        if (!"accept".equals(invocation.getExecutable().getSimpleName())) {
            return false;
        }
        final CtTypeReference<?> declaring = invocation.getExecutable().getDeclaringType();
        return declaring != null && CONNECTION_FQN.equals(declaring.getQualifiedName());
    }

    private static void rewrite(final CtInvocation<?> invocation, final Launcher launcher) {
        final CtExpression<?> headers = invocation.getArguments().get(1);
        final CtTypeReference<?> type = launcher.getFactory().Type().createReference(HEADERS_FROM_FQN);
        headers.replace(launcher.getFactory().Code().createConstructorCall(type, headers.clone()));
    }
}
