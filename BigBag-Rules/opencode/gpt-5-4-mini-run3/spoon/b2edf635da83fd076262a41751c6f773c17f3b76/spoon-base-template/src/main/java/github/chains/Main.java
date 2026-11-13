package github.chains;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import spoon.Launcher;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtExpression;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

public class Main {
    private static final String TARGET_CONSTRUCTOR = "org.yaml.snakeyaml.constructor.Constructor";
    private static final String LOADER_OPTIONS = "org.yaml.snakeyaml.LoaderOptions";

    public static void main(String[] args) {
        if (args.length < 1) {
            throw new IllegalArgumentException("usage: <input-source-dir> [output-dir]");
        }

        Path input = Paths.get(args[0]);
        Path output = args.length > 1 ? Paths.get(args[1]) : input.resolveSibling(input.getFileName() + "-fixed");

        try {
            Files.createDirectories(output);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.addInputResource(input.toString());
        launcher.setSourceOutputDirectory(output.toString());
        launcher.buildModel();

        applyTransformation(launcher);
        launcher.prettyprint();
    }

    private static void applyTransformation(Launcher launcher) {
        List<CtConstructorCall> calls = launcher.getModel().getElements(new TypeFilter<>(CtConstructorCall.class));
        for (CtConstructorCall<?> call : calls) {
            if (!isTargetConstructor(call)) {
                continue;
            }
            if (hasLoaderOptionsArgument(call)) {
                continue;
            }
            CtExpression<?> loaderOptions = createLoaderOptionsCall(launcher);
            call.addArgument(loaderOptions);
        }
    }

    private static boolean isTargetConstructor(CtConstructorCall<?> call) {
        CtTypeReference<?> type = call.getType();
        return type != null && TARGET_CONSTRUCTOR.equals(type.getQualifiedName());
    }

    private static boolean hasLoaderOptionsArgument(CtConstructorCall<?> call) {
        List<CtExpression<?>> arguments = call.getArguments();
        if (arguments.isEmpty()) {
            return false;
        }
        CtExpression<?> last = arguments.get(arguments.size() - 1);
        CtTypeReference<?> lastType = last.getType();
        return lastType != null && LOADER_OPTIONS.equals(lastType.getQualifiedName());
    }

    private static CtExpression<?> createLoaderOptionsCall(Launcher launcher) {
        CtTypeReference<?> loaderOptionsType = launcher.getFactory().Type().createReference(LOADER_OPTIONS);
        return launcher.getFactory().Code().createConstructorCall(loaderOptionsType);
    }
}
