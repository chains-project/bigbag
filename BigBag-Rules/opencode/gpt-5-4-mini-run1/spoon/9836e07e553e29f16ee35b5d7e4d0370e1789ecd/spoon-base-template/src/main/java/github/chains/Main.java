package github.chains;

import java.nio.file.Path;
import java.nio.file.Paths;
import spoon.Launcher;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtExpression;
import spoon.reflect.factory.Factory;
import spoon.reflect.visitor.filter.TypeFilter;

public final class Main {

    private static final String STRING_CONTAINS = "org.hamcrest.core.StringContains";

    private static final String STRING_STARTS_WITH = "org.hamcrest.core.StringStartsWith";

    private static final String MATCHERS = "org.hamcrest.CoreMatchers";

    private Main() {
        // no-op
    }

    public static void main(final String[] args) {
        if (args.length < 2) {
            throw new IllegalArgumentException("Usage: Main <input-src-dir> <output-src-dir>");
        }
        final Path input = Paths.get(args[0]);
        final Path output = Paths.get(args[1]);
        final Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.addInputResource(input.toString());
        launcher.setSourceOutputDirectory(output.toFile());
        launcher.buildModel();
        final Factory factory = launcher.getFactory();
        for (final CtConstructorCall<?> call : launcher.getModel().getElements(
            new TypeFilter<>(CtConstructorCall.class)
        )) {
            transform(call, factory);
        }
        launcher.prettyprint();
    }

    private static void transform(final CtConstructorCall<?> call, final Factory factory) {
        final String type = call.getType().getQualifiedName();
        if (!STRING_CONTAINS.equals(type) && !STRING_STARTS_WITH.equals(type)) {
            return;
        }
        if (call.getArguments().size() != 2) {
            return;
        }
        final CtExpression<?> ignoreCase = call.getArguments().get(0);
        final CtExpression<?> text = call.getArguments().get(1);
        final String factoryMethod;
        final String ignoreFactoryMethod;
        if (STRING_CONTAINS.equals(type)) {
            factoryMethod = "containsString";
            ignoreFactoryMethod = "containsStringIgnoringCase";
        } else {
            factoryMethod = "startsWith";
            ignoreFactoryMethod = "startsWithIgnoringCase";
        }
        final String replacement = String.format(
            "(%s) ? %s.%s(%s) : %s.%s(%s)",
            ignoreCase,
            MATCHERS,
            ignoreFactoryMethod,
            text,
            MATCHERS,
            factoryMethod,
            text
        );
        call.replace(factory.Code().createCodeSnippetExpression(replacement));
    }
}
