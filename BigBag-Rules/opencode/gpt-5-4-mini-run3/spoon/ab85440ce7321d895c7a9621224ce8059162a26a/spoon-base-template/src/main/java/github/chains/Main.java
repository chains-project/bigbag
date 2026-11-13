package github.chains;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import spoon.Launcher;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.visitor.filter.TypeFilter;

public final class Main {

    private static final Set<String> TARGET_TYPES = Set.of(
        "org.hamcrest.core.StringContains",
        "org.hamcrest.core.StringStartsWith"
    );

    private Main() {
    }

    public static void main(final String[] args) {
        if (args.length < 1) {
            throw new IllegalArgumentException("Expected project root path");
        }
        final File project = new File(args[0]);
        if (!project.isDirectory()) {
            throw new IllegalArgumentException("Project directory not found: " + project);
        }
        final File output = args.length > 1 ? new File(args[1]) : new File(project, "spoon-out");
        final Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(true);
        launcher.getEnvironment().setIgnoreDuplicateDeclarations(true);
        launcher.getEnvironment().setSourceOutputDirectory(output);
        final File main = new File(project, "src/main/java");
        final File test = new File(project, "src/test/java");
        if (main.isDirectory()) {
            launcher.addInputResource(main.getAbsolutePath());
        }
        if (test.isDirectory()) {
            launcher.addInputResource(test.getAbsolutePath());
        }
        launcher.buildModel();
        for (final CtConstructorCall<?> call : launcher.getModel().getElements(
            new TypeFilter<>(CtConstructorCall.class)
        )) {
            final String type = call.getType() == null ? null : call.getType().getQualifiedName();
            if (TARGET_TYPES.contains(type) && call.getArguments().size() == 2) {
                call.removeArgument(call.getArguments().get(0));
            }
        }
        launcher.prettyprint();
        try {
            writeCompatibilityShims(output.toPath());
        } catch (final IOException err) {
            throw new IllegalStateException(err);
        }
    }

    private static void writeCompatibilityShims(final Path output) throws IOException {
        write(output, "org/hamcrest/core/AllOf.java", """
            package org.hamcrest.core;

            import java.util.Arrays;
            import java.util.Iterator;
            import org.hamcrest.BaseMatcher;
            import org.hamcrest.Description;
            import org.hamcrest.Matcher;

            public final class AllOf<T> extends BaseMatcher<T> {

                private final Matcher<? super T>[] matchers;

                @SafeVarargs
                public AllOf(final Matcher<? super T>... matchers) {
                    this.matchers = matchers.clone();
                }

                public AllOf(final Iterable<Matcher<? super T>> matchers) {
                    this(toArray(matchers));
                }

                @Override
                public boolean matches(final Object item) {
                    for (final Matcher<? super T> matcher : this.matchers) {
                        if (!matcher.matches(item)) {
                            return false;
                        }
                    }
                    return true;
                }

                @Override
                public void describeTo(final Description description) {
                    description.appendList("(", " and ", ")", Arrays.asList(this.matchers));
                }

                private static <T> Matcher<? super T>[] toArray(
                    final Iterable<Matcher<? super T>> matchers
                ) {
                    final java.util.ArrayList<Matcher<? super T>> items = new java.util.ArrayList<>();
                    for (final Matcher<? super T> matcher : matchers) {
                        items.add(matcher);
                    }
                    @SuppressWarnings("unchecked")
                    final Matcher<? super T>[] array = items.toArray(new Matcher[0]);
                    return array;
                }
            }
            """);
        write(output, "org/hamcrest/core/StringContains.java", """
            package org.hamcrest.core;

            import java.util.Locale;
            import org.hamcrest.Description;
            import org.hamcrest.TypeSafeMatcher;

            public final class StringContains extends TypeSafeMatcher<String> {

                private final boolean ignoreCase;

                private final String substring;

                public StringContains(final String substring) {
                    this(false, substring);
                }

                public StringContains(final boolean ignoreCase, final String substring) {
                    this.ignoreCase = ignoreCase;
                    this.substring = substring;
                }

                @Override
                protected boolean matchesSafely(final String item) {
                    return this.normalize(item).contains(this.normalize(this.substring));
                }

                @Override
                public void describeTo(final Description description) {
                    description.appendText("a string containing ").appendValue(this.substring);
                }

                private String normalize(final String value) {
                    return this.ignoreCase ? value.toLowerCase(Locale.ROOT) : value;
                }
            }
            """);
        write(output, "org/hamcrest/core/StringStartsWith.java", """
            package org.hamcrest.core;

            import java.util.Locale;
            import org.hamcrest.Description;
            import org.hamcrest.TypeSafeMatcher;

            public final class StringStartsWith extends TypeSafeMatcher<String> {

                private final boolean ignoreCase;

                private final String prefix;

                public StringStartsWith(final String prefix) {
                    this(false, prefix);
                }

                public StringStartsWith(final boolean ignoreCase, final String prefix) {
                    this.ignoreCase = ignoreCase;
                    this.prefix = prefix;
                }

                @Override
                protected boolean matchesSafely(final String item) {
                    return this.normalize(item).startsWith(this.normalize(this.prefix));
                }

                @Override
                public void describeTo(final Description description) {
                    description.appendText("a string starting with ").appendValue(this.prefix);
                }

                private String normalize(final String value) {
                    return this.ignoreCase ? value.toLowerCase(Locale.ROOT) : value;
                }
            }
            """);
    }

    private static void write(final Path output, final String relative, final String content)
        throws IOException {
        final Path file = output.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content, StandardCharsets.UTF_8);
    }
}
