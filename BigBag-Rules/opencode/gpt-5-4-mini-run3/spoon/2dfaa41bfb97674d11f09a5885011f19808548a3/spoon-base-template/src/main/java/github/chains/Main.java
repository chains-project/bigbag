package github.chains;

import java.nio.file.Path;

import spoon.MavenLauncher;
import spoon.Launcher;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtExpression;
import spoon.reflect.visitor.filter.TypeFilter;

public class Main {
    private static final String OLD_METHOD = "getArgumentAt";
    private static final String NEW_METHOD = "getArgument";

    public static void main(String[] args) {
        if (args.length < 2) {
            throw new IllegalArgumentException("Usage: Main <input-source-dir> <output-dir>");
        }

        Path input = Path.of(args[0]);
        Path output = Path.of(args[1]);

        Launcher launcher = new MavenLauncher(input.toString(), MavenLauncher.SOURCE_TYPE.ALL_SOURCE);
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setIgnoreSyntaxErrors(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.setSourceOutputDirectory(output.toFile());

        launcher.buildModel().getElements(new TypeFilter<>(CtInvocation.class)).forEach(Main::rewriteInvocation);
        launcher.prettyprint();
    }

    private static void rewriteInvocation(CtInvocation<?> invocation) {
        if (!OLD_METHOD.equals(invocation.getExecutable().getSimpleName())) {
            return;
        }
        if (invocation.getArguments().size() != 2) {
            return;
        }
        String rewritten = invocation.toString().replace(OLD_METHOD, NEW_METHOD);
        CtExpression<?> replacement = invocation.getFactory().Code().createCodeSnippetExpression(rewritten);
        invocation.replace(replacement);
    }
}
