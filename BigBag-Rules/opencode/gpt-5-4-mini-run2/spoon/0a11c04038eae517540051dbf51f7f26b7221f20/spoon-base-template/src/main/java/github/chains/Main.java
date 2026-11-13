package github.chains;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.reference.CtExecutableReference;

import java.io.File;

public class Main {
    private static final String CONSTRUCTOR_TYPE = "org.yaml.snakeyaml.constructor.Constructor";
    private static final String LOADER_OPTIONS_TYPE = "org.yaml.snakeyaml.LoaderOptions";

    public static void main(String[] args) {
        if (args.length < 1) {
            throw new IllegalArgumentException("Expected a source directory");
        }

        String sourceDir = args[0];
        String outputDir = args.length > 1 ? args[1] : sourceDir;

        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(false);
        launcher.getEnvironment().setSourceOutputDirectory(new File(outputDir));
        launcher.addInputResource(sourceDir);
        launcher.addProcessor(new AbstractProcessor<CtConstructorCall<?>>() {
            @Override
            public void process(CtConstructorCall<?> call) {
                if (!isOldSnakeYamlConstructor(call)) {
                    return;
                }

                call.addArgument(call.getFactory().Code().createCodeSnippetExpression(
                        "new " + LOADER_OPTIONS_TYPE + "()"));
            }
        });

        launcher.run();
    }

    private static boolean isOldSnakeYamlConstructor(CtConstructorCall<?> call) {
        CtExecutableReference<?> executable = call.getExecutable();
        return executable != null
                && executable.getDeclaringType() != null
                && CONSTRUCTOR_TYPE.equals(executable.getDeclaringType().getQualifiedName())
                && executable.getParameters().size() == 1
                && call.getArguments().size() == 1;
    }
}
