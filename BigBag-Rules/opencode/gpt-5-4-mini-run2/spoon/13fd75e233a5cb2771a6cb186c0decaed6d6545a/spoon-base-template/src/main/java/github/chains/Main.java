package github.chains;

import spoon.Launcher;
import spoon.compiler.Environment;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtLiteral;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

public class Main {
    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: <inputDir> <outputDir>");
            System.exit(1);
        }
        final Launcher launcher = new Launcher();
        final Environment env = launcher.getEnvironment();
        env.setNoClasspath(true);
        env.setAutoImports(true);
        env.setCommentEnabled(true);
        launcher.addInputResource(args[0]);
        launcher.buildModel();
        launcher.getModel().getElements(new TypeFilter<>(CtConstructorCall.class)).forEach(call -> {
            if (isLegacySubstringMatcher(call)) {
                rewriteSubstringMatcher(call);
            }
        });
        launcher.setSourceOutputDirectory(args[1]);
        launcher.prettyprint();
    }

    private static boolean isLegacySubstringMatcher(final CtConstructorCall<?> call) {
        final CtTypeReference<?> type = call.getType();
        if (type == null) {
            return false;
        }
        final String name = type.getQualifiedName();
        return "org.hamcrest.core.StringContains".equals(name)
            || "org.hamcrest.core.StringStartsWith".equals(name)
            || "org.hamcrest.core.StringEndsWith".equals(name);
    }

    private static void rewriteSubstringMatcher(final CtConstructorCall<?> call) {
        if (call.getArguments().size() != 2) {
            return;
        }
        final CtExpression<?> first = call.getArguments().get(0);
        if (!(first instanceof CtLiteral)) {
            return;
        }
        final Object value = ((CtLiteral<?>) first).getValue();
        if (!(value instanceof Boolean)) {
            return;
        }
        final boolean ignoreCase = (Boolean) value;
        final CtExpression<?> text = call.getArguments().get(1);
        final String fqcn = call.getType().getQualifiedName();
        final String replacement;
        if ("org.hamcrest.core.StringContains".equals(fqcn)) {
            replacement = ignoreCase
                ? "org.hamcrest.core.StringContains.containsStringIgnoringCase(" + text + ")"
                : "org.hamcrest.core.StringContains.containsString(" + text + ")";
        } else if ("org.hamcrest.core.StringStartsWith".equals(fqcn)) {
            replacement = ignoreCase
                ? "org.hamcrest.core.StringStartsWith.startsWithIgnoringCase(" + text + ")"
                : "org.hamcrest.core.StringStartsWith.startsWith(" + text + ")";
        } else if ("org.hamcrest.core.StringEndsWith".equals(fqcn)) {
            replacement = ignoreCase
                ? "org.hamcrest.core.StringEndsWith.endsWithIgnoringCase(" + text + ")"
                : "org.hamcrest.core.StringEndsWith.endsWith(" + text + ")";
        } else {
            return;
        }
        call.replace(call.getFactory().Code().createCodeSnippetExpression(replacement));
    }
}
