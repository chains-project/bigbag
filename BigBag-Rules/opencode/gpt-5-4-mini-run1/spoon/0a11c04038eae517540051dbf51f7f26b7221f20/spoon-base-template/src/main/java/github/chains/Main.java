package github.chains;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtExpression;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtTypeReference;

public class Main {
    private static final String BROKEN_TYPE = "org.yaml.snakeyaml.constructor.Constructor";
    private static final String LOADER_OPTIONS_TYPE = "org.yaml.snakeyaml.LoaderOptions";

    public static void main(String[] args) {
        if (args.length < 2) {
            throw new IllegalArgumentException("Usage: Main <input-source-dir> <output-source-dir>");
        }

        Launcher launcher = new Launcher();
        launcher.addInputResource(args[0]);
        launcher.setSourceOutputDirectory(args[1]);

        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(true);

        launcher.addProcessor(new AbstractProcessor<CtConstructorCall<?>>() {
            @Override
            public boolean isToBeProcessed(CtConstructorCall<?> constructorCall) {
                CtExecutableReference<?> executable = constructorCall.getExecutable();
                if (executable == null) {
                    return false;
                }

                CtTypeReference<?> declaringType = executable.getDeclaringType();
                return declaringType != null
                        && BROKEN_TYPE.equals(declaringType.getQualifiedName())
                        && executable.getParameters().size() == 1;
            }

            @Override
            public void process(CtConstructorCall<?> constructorCall) {
                CtExpression<?> argument = constructorCall.getArguments().get(0);
                CtTypeReference<?> loaderOptionsType = getFactory().Type().createReference(LOADER_OPTIONS_TYPE);
                CtConstructorCall<?> loaderOptions = getFactory().Code().createConstructorCall(loaderOptionsType);

                CtTypeReference<?> brokenType = constructorCall.getExecutable().getDeclaringType();
                CtConstructorCall<?> replacement = getFactory().Code().createConstructorCall(
                        brokenType,
                        argument,
                        loaderOptions
                );

                constructorCall.replace(replacement);
            }
        });

        launcher.run();
    }
}
