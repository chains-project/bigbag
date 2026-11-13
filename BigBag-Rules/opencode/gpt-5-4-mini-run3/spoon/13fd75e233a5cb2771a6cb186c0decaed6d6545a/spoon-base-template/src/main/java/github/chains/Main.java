package github.chains;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtExpression;

public class Main {

    private static final String STRING_CONTAINS = "org.hamcrest.core.StringContains";

    private static final String STRING_STARTS_WITH = "org.hamcrest.core.StringStartsWith";

    private static final String STRING_ENDS_WITH = "org.hamcrest.core.StringEndsWith";

    public static void main(final String[] args) {
        if (args.length < 2) {
            throw new IllegalArgumentException("Usage: Main <inputDir> <outputDir>");
        }
        final Path input = Paths.get(args[0]);
        final Path output = Paths.get(args[1]);
        final Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.addInputResource(input.toString());
        launcher.setSourceOutputDirectory(output.toString());
        launcher.addProcessor(new AbstractProcessor<CtConstructorCall<?>>() {
            @Override
            public boolean isToBeProcessed(final CtConstructorCall<?> candidate) {
                return candidate.getType() != null
                    && isAffectedType(candidate.getType().getQualifiedName())
                    && candidate.getArguments().size() == 2;
            }

            @Override
            public void process(final CtConstructorCall<?> call) {
                final List<CtExpression<?>> arguments = call.getArguments();
                final CtExpression<?> ignoreCase = arguments.get(0);
                final CtExpression<?> substring = arguments.get(1);
                final String replacement = replacementFor(
                    call.getType().getQualifiedName(),
                    ignoreCase,
                    substring
                );
                call.replace(call.getFactory().Code().createCodeSnippetExpression(replacement));
            }
        });
        launcher.run();
    }

    private static boolean isAffectedType(final String type) {
        return STRING_CONTAINS.equals(type)
            || STRING_STARTS_WITH.equals(type)
            || STRING_ENDS_WITH.equals(type);
    }

    private static String replacementFor(
        final String type,
        final CtExpression<?> ignoreCase,
        final CtExpression<?> substring
    ) {
        final String positive;
        final String negative;
        if (STRING_STARTS_WITH.equals(type)) {
            positive = "org.hamcrest.Matchers.startsWithIgnoringCase";
            negative = "org.hamcrest.Matchers.startsWith";
        } else if (STRING_ENDS_WITH.equals(type)) {
            positive = "org.hamcrest.Matchers.endsWithIgnoringCase";
            negative = "org.hamcrest.Matchers.endsWith";
        } else {
            positive = "org.hamcrest.Matchers.containsStringIgnoringCase";
            negative = "org.hamcrest.Matchers.containsString";
        }
        if ("true".equals(ignoreCase.toString())) {
            return String.format("%s(%s)", positive, substring);
        }
        if ("false".equals(ignoreCase.toString())) {
            return String.format("%s(%s)", negative, substring);
        }
        return String.format(
            "(%s ? %s(%s) : %s(%s))",
            ignoreCase,
            positive,
            substring,
            negative,
            substring
        );
    }
}
