package github.chains;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import spoon.Launcher;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.visitor.filter.TypeFilter;

public final class Main {

    private static final List<String> TARGET_TYPES = List.of(
        "org.hamcrest.core.StringContains",
        "org.hamcrest.core.StringStartsWith",
        "org.hamcrest.core.StringEndsWith"
    );

    private Main() {
    }

    public static void main(final String[] args) {
        if (args.length < 1 || args.length > 2) {
            throw new IllegalArgumentException("Usage: Main <input-source-dir> [<output-source-dir>]");
        }
        final File input = new File(args[0]);
        final File output = new File(args.length > 1 ? args[1] : args[0]);
        if (new File(input, "pom.xml").isFile()) {
            transformDir(new File(input, "src/main/java"), new File(output, "src/main/java"));
            transformDir(new File(input, "src/test/java"), new File(output, "src/test/java"));
        } else {
            transformDir(input, output);
        }
    }

    private static void transformDir(final File input, final File output) {
        if (!input.isDirectory()) {
            return;
        }
        final Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(true);
        launcher.getEnvironment().setComplianceLevel(11);
        launcher.addInputResource(input.getPath());
        launcher.setSourceOutputDirectory(output);
        launcher.buildModel();
        final List<CtConstructorCall<?>> calls = new ArrayList<>(
            launcher.getModel().getElements(new TypeFilter<>(CtConstructorCall.class))
        );
        for (final CtConstructorCall<?> call : calls) {
            transform(call);
        }
        launcher.prettyprint();
    }

    private static void transform(final CtConstructorCall<?> call) {
        final String text = call.toString();
        if (!text.contains("StringContains(")
            && !text.contains("StringStartsWith(")
            && !text.contains("StringEndsWith(")) {
            return;
        }
        if (call.getArguments().size() != 2) {
            return;
        }
        call.setArguments(List.of(call.getArguments().get(1)));
    }
}
