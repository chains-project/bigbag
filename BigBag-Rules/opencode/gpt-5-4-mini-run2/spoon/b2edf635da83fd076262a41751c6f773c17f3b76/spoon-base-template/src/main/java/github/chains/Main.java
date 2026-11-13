package github.chains;

import java.io.File;
import java.util.List;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtExpression;
import spoon.reflect.reference.CtTypeReference;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            throw new IllegalArgumentException("Usage: Main <input-source-dir> [output-source-dir]");
        }

        String inputDir = args[0];
        String outputDir = args.length > 1 ? args[1] : inputDir + "-fixed";

        Launcher launcher = new Launcher();
        launcher.addInputResource(inputDir);
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(true);
        launcher.getEnvironment().setIgnoreDuplicateDeclarations(true);
        launcher.setSourceOutputDirectory(new File(outputDir));

        launcher.addProcessor(new AbstractProcessor<CtConstructorCall<?>>() {
            @Override
            public void process(CtConstructorCall<?> call) {
                CtTypeReference<?> type = call.getType();
                if (type == null || !"org.yaml.snakeyaml.constructor.Constructor".equals(type.getQualifiedName())) {
                    return;
                }

                List<CtExpression<?>> arguments = call.getArguments();
                if (arguments.size() != 1) {
                    return;
                }

                CtExpression<?> loaderOptions = getFactory().Code().createCodeSnippetExpression(
                        "new org.yaml.snakeyaml.LoaderOptions()");
                call.addArgument(loaderOptions);
            }
        });

        launcher.run();
    }
}
