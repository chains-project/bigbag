package github.chains;

import spoon.Launcher;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtExpression;
import spoon.reflect.factory.Factory;
import spoon.reflect.visitor.filter.TypeFilter;

import java.nio.file.Path;

public class Main {

    private static final String OLD_CONSTRUCTOR_TYPE = "org.yaml.snakeyaml.constructor.Constructor";
    private static final String LOADER_OPTIONS_TYPE = "org.yaml.snakeyaml.LoaderOptions";

    public static void main(String[] args) {
        if (args.length < 1) {
            throw new IllegalArgumentException("Expected input source directory as the first argument");
        }

        Path inputDir = Path.of(args[0]);
        Path outputDir = args.length > 1 ? Path.of(args[1]) : inputDir;

        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.addInputResource(inputDir.toString());
        launcher.setSourceOutputDirectory(outputDir.toFile());
        launcher.buildModel();

        Factory factory = launcher.getFactory();
        for (CtConstructorCall<?> constructorCall : launcher.getModel().getElements(new TypeFilter<>(CtConstructorCall.class))) {
            if (!shouldRewrite(constructorCall)) {
                continue;
            }

            CtExpression<?> existingArgument = constructorCall.getArguments().get(0).clone();
            CtConstructorCall<?> loaderOptions = factory.Code().createConstructorCall(
                    factory.Type().createReference(LOADER_OPTIONS_TYPE)
            );
            CtConstructorCall<?> replacement = factory.Code().createConstructorCall(
                    factory.Type().createReference(OLD_CONSTRUCTOR_TYPE),
                    existingArgument,
                    loaderOptions
            );
            constructorCall.replace(replacement);
        }

        launcher.prettyprint();
    }

    private static boolean shouldRewrite(CtConstructorCall<?> constructorCall) {
        if (constructorCall.getType() == null || !OLD_CONSTRUCTOR_TYPE.equals(constructorCall.getType().getQualifiedName())) {
            return false;
        }
        if (constructorCall.getArguments().size() != 1) {
            return false;
        }

        CtExpression<?> argument = constructorCall.getArguments().get(0);
        return argument.getType() != null && argument.getType().getQualifiedName().startsWith("java.lang.Class");
    }
}
