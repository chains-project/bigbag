package github.chains;

import java.util.List;
import java.nio.file.Files;
import java.nio.file.Path;
import spoon.Launcher;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtExpression;
import spoon.reflect.visitor.filter.TypeFilter;

public class Main {

    public static void main(final String[] args) {
        if (args.length != 2) {
            throw new IllegalArgumentException("Usage: <input-dir> <output-dir>");
        }
        final Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(true);
        addInputRoots(launcher, Path.of(args[0]));
        launcher.setSourceOutputDirectory(args[1]);
        launcher.buildModel();
        transform(launcher);
        launcher.prettyprint();
    }

    private static void addInputRoots(final Launcher launcher, final Path root) {
        final Path main = root.resolve("src/main/java");
        final Path test = root.resolve("src/test/java");
        if (Files.isDirectory(main)) {
            launcher.addInputResource(main.toString());
        } else if (Files.isDirectory(test)) {
            launcher.addInputResource(test.toString());
        } else {
            launcher.addInputResource(root.toString());
        }
    }

    private static void transform(final Launcher launcher) {
        final List<CtConstructorCall<?>> ctors = launcher.getModel().getElements(
            new TypeFilter<>(CtConstructorCall.class)
        );
        for (final CtConstructorCall<?> ctor : ctors) {
            if (isOldStringMatcherPattern(ctor)) {
                final boolean ignoreCase = Boolean.parseBoolean(ctor.getArguments().get(0).toString());
                final String text = ctor.getArguments().get(1).toString();
                ctor.replace(
                    ctor.getFactory().Code().createCodeSnippetExpression(
                        matcherFactoryCall(ctor.getType().getSimpleName(), ignoreCase, text)
                    )
                );
            }
        }
    }

    private static String matcherFactoryCall(
        final String type,
        final boolean ignoreCase,
        final String text
    ) {
        final String suffix = ignoreCase ? "IgnoringCase" : "";
        if ("StringContains".equals(type)) {
            return String.format("org.hamcrest.core.StringContains.containsString%s(%s)", suffix, text);
        }
        if ("StringStartsWith".equals(type)) {
            return String.format("org.hamcrest.core.StringStartsWith.startsWith%s(%s)", suffix, text);
        }
        return String.format("org.hamcrest.core.StringEndsWith.endsWith%s(%s)", suffix, text);
    }

    private static boolean isOldStringMatcherPattern(final CtConstructorCall<?> ctor) {
        final String type = ctor.getType() == null ? "" : ctor.getType().getSimpleName();
        return ("StringContains".equals(type)
            || "StringStartsWith".equals(type)
            || "StringEndsWith".equals(type))
            && ctor.getArguments().size() == 2
            && ctor.getArguments().get(0).toString().matches("true|false");
    }
}
