package github.chains;

import java.nio.file.Path;
import java.nio.file.Paths;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtTypeReference;

public class Main {

    private static final String JASPER_PEN_TYPE = "net.sf.jasperreports.engine.JRPen";
    private static final String METHOD_NAME = "setLineWidth";

    public static void main(String[] args) {
        if (args.length < 1 || args.length > 2) {
            throw new IllegalArgumentException("Usage: Main <input-source-dir> [output-source-dir]");
        }

        final Path inputDir = Paths.get(args[0]);
        final Path outputDir = args.length == 2 ? Paths.get(args[1]) : inputDir;

        final Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(false);
        launcher.addInputResource(inputDir.toString());
        launcher.setSourceOutputDirectory(outputDir.toFile());
        launcher.addProcessor(new AbstractProcessor<CtInvocation<?>>() {
            @Override
            public void process(CtInvocation<?> invocation) {
                if (!shouldTransform(invocation)) {
                    return;
                }

                final CtExpression<?> originalArgument = invocation.getArguments().get(0);
                final CtExpression<?> replacement = getFactory()
                    .createCodeSnippetExpression("java.lang.Float.valueOf((float)(" + originalArgument + "))");
                originalArgument.replace(replacement);
            }

            private boolean shouldTransform(CtInvocation<?> invocation) {
                final CtExecutableReference<?> executable = invocation.getExecutable();
                if (executable == null || !METHOD_NAME.equals(executable.getSimpleName())) {
                    return false;
                }

                if (invocation.getArguments().size() != 1) {
                    return false;
                }

                final CtTypeReference<?> declaringType = executable.getDeclaringType();
                if (declaringType == null) {
                    return false;
                }

                return declaringType.isSubtypeOf(getFactory().Type().createReference(JASPER_PEN_TYPE));
            }
        });
        launcher.run();
    }
}
