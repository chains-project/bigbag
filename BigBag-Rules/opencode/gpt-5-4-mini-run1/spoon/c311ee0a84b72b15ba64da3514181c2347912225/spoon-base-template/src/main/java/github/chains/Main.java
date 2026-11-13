package github.chains;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import spoon.Launcher;
import spoon.reflect.code.CtCodeSnippetExpression;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtExpression;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.visitor.filter.TypeFilter;

public final class Main {

    private Main() {
        // no instances
    }

    public static void main(final String[] args) {
        if (args.length < 1) {
            throw new IllegalArgumentException("Usage: Main <input-dir> [output-dir]");
        }
        final File input = new File(args[0]);
        final File output = args.length > 1 ? new File(args[1]) : input;
        final Launcher launcher = new Launcher();
        for (final String file : javaFiles(input.toPath())) {
            launcher.addInputResource(file);
        }
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.setSourceOutputDirectory(output);
        launcher.buildModel();
        transform(launcher);
        launcher.prettyprint();
    }

    private static List<String> javaFiles(final Path root) {
        final List<String> files = new ArrayList<>();
        try {
            Files.walk(root)
                .filter(path -> Files.isRegularFile(path))
                .filter(path -> path.toString().endsWith(".java"))
                .filter(path -> !path.getFileName().toString().equals("package-info.java"))
                .forEach(path -> files.add(path.toString()));
        } catch (final IOException err) {
            throw new IllegalStateException(err);
        }
        return files;
    }

    private static void transform(final Launcher launcher) {
        final List<CtConstructorCall> calls = launcher.getModel().getElements(
            new TypeFilter<>(CtConstructorCall.class)
        );
        for (final CtConstructorCall<?> call : calls) {
            final String type = call.getType() == null ? null : call.getType().getQualifiedName();
            if (type != null && call.getArguments().size() == 2) {
                replace(call, type);
            }
        }
    }

    private static void replace(final CtConstructorCall<?> call, final String type) {
        final CtExpression<?> first = call.getArguments().get(0).clone();
        final CtExpression<?> second = call.getArguments().get(1).clone();
        final String replacement;
        if ("org.hamcrest.core.StringContains".equals(type)) {
            replacement = String.format(
                "(%s ? org.hamcrest.Matchers.containsStringIgnoringCase(%s) : org.hamcrest.Matchers.containsString(%s))",
                first,
                second,
                second
            );
        } else if ("org.hamcrest.core.StringStartsWith".equals(type)) {
            replacement = String.format(
                "(%s ? org.hamcrest.Matchers.startsWithIgnoringCase(%s) : org.hamcrest.Matchers.startsWith(%s))",
                first,
                second,
                second
            );
        } else {
            return;
        }
        final CtCodeSnippetExpression<?> snippet = call.getFactory().Code()
            .createCodeSnippetExpression(replacement);
        final CtElement current = call;
        current.replace(snippet);
    }
}
