package github.chains;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import spoon.Launcher;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtExpression;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

public final class Main {

    private static final String HAMCREST_CONTAINS = "StringContains";

    private static final String HAMCREST_STARTS_WITH = "StringStartsWith";

    private Main() {
        // utility class
    }

    public static void main(final String[] args) {
        final File source = args.length > 0 ? new File(args[0]) : new File(".");
        final File output = args.length > 1 ? new File(args[1]) : source;
        final Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(false);
        launcher.getEnvironment().setCommentEnabled(true);
        addJavaSources(launcher, source.toPath());
        launcher.setSourceOutputDirectory(output);
        launcher.buildModel();
        final int changes = rewriteDeprecatedStringMatchers(launcher);
        launcher.prettyprint();
        System.out.printf("Applied %d transformation(s)%n", changes);
    }

    private static int rewriteDeprecatedStringMatchers(final Launcher launcher) {
        int changes = 0;
        for (CtConstructorCall<?> call : launcher.getModel().getElements(
            new TypeFilter<>(CtConstructorCall.class)
        )) {
            final String type = simpleName(call.getType());
            if (!HAMCREST_CONTAINS.equals(type) && !HAMCREST_STARTS_WITH.equals(type)) {
                continue;
            }
            final List<CtExpression<?>> args = call.getArguments();
            if (args.size() != 2 || !isBooleanish(args.get(0))) {
                continue;
            }
            args.remove(0);
            changes++;
        }
        return changes;
    }

    private static boolean isBooleanish(final CtExpression<?> expression) {
        final CtTypeReference<?> type = expression.getType();
        if (type != null) {
            final String qname = type.getQualifiedName();
            if ("boolean".equals(qname) || Boolean.class.getName().equals(qname)) {
                return true;
            }
        }
        final String printed = expression.toString();
        return "true".equals(printed) || "false".equals(printed);
    }

    private static String simpleName(final CtTypeReference<?> type) {
        return type == null ? "" : type.getSimpleName();
    }

    private static void addJavaSources(final Launcher launcher, final Path source) {
        try {
            if (Files.isRegularFile(source) && source.toString().endsWith(".java")) {
                launcher.addInputResource(source.toString());
                return;
            }
            Files.walk(source)
                .filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                .filter(path -> !path.getFileName().toString().equals("package-info.java"))
                .forEach(path -> launcher.addInputResource(path.toString()));
        } catch (final IOException err) {
            throw new IllegalStateException("Failed to read source tree " + source, err);
        }
    }
}
