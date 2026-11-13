package github.chains;

import java.util.Collections;

import spoon.Launcher;
import spoon.reflect.factory.CoreFactory;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.reference.CtWildcardReference;
import spoon.reflect.visitor.filter.TypeFilter;

public final class Main {
    private static final String TARGET_TYPE = "org.snmp4j.agent.ManagedObject";

    private Main() {
    }

    public static void main(final String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: Main <input-source-dir> <output-source-dir>");
            System.exit(1);
        }

        final Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.addInputResource(args[0]);
        launcher.setSourceOutputDirectory(args[1]);
        launcher.buildModel();

        final Factory factory = launcher.getFactory();
        for (final CtTypeReference<?> reference : launcher.getModel().getElements(new TypeFilter<>(CtTypeReference.class))) {
            if (shouldRewrite(reference)) {
                rewrite(reference, factory);
            }
        }

        launcher.prettyprint();
    }

    private static boolean shouldRewrite(final CtTypeReference<?> reference) {
        final String qualifiedName = reference.getQualifiedName();
        return (TARGET_TYPE.equals(qualifiedName) || TARGET_TYPE.endsWith(reference.getSimpleName()))
                && reference.getActualTypeArguments().isEmpty()
                && reference.getParent(CtTypeReference.class) != null;
    }

    private static void rewrite(final CtTypeReference<?> reference, final Factory factory) {
        final CtWildcardReference wildcard = factory.Core().createWildcardReference();
        final CtTypeReference<?> replacement = factory.Type().createReference(TARGET_TYPE);
        replacement.addActualTypeArgument(wildcard);
        reference.replace(replacement);
    }
}
