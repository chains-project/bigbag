package github.chains;

import spoon.Launcher;
import spoon.compiler.Environment;
import spoon.processing.AbstractProcessor;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtType;
import spoon.reflect.reference.CtTypeReference;

import java.nio.file.Path;
import java.nio.file.Paths;

public class Main {
    private static final String TARGET_SUPERCLASS = "org.yaml.snakeyaml.representer.Representer";
    private static final String TARGET_METHOD_NAME = "getProperties";
    private static final String TARGET_EXCEPTION = "java.beans.IntrospectionException";

    public static void main(String[] args) {
        if (args.length < 1 || args.length > 2) {
            throw new IllegalArgumentException("Usage: Main <input-source-dir> [output-dir]");
        }

        Path inputDir = Paths.get(args[0]).toAbsolutePath().normalize();
        Path outputDir = args.length == 2 ? Paths.get(args[1]).toAbsolutePath().normalize() : inputDir;

        Launcher launcher = new Launcher();
        launcher.addInputResource(inputDir.toString());
        launcher.setSourceOutputDirectory(outputDir.toFile());

        Environment environment = launcher.getEnvironment();
        environment.setNoClasspath(true);
        environment.setAutoImports(true);

        launcher.addProcessor(new AbstractProcessor<CtMethod<?>>() {
            @Override
            public void process(CtMethod<?> method) {
                if (!matchesBreakingOverride(method)) {
                    return;
                }

                method.removeThrownType(method.getFactory().Type().createReference(TARGET_EXCEPTION));
            }
        });

        launcher.run();
    }

    private static boolean matchesBreakingOverride(CtMethod<?> method) {
        if (!TARGET_METHOD_NAME.equals(method.getSimpleName())) {
            return false;
        }

        CtType<?> declaringType = method.getDeclaringType();
        if (declaringType == null) {
            return false;
        }

        CtTypeReference<?> targetType = method.getFactory().Type().createReference(TARGET_SUPERCLASS);
        if (!declaringType.isSubtypeOf(targetType)) {
            return false;
        }

        for (CtTypeReference<? extends Throwable> thrownType : method.getThrownTypes()) {
            if (TARGET_EXCEPTION.equals(thrownType.getQualifiedName())) {
                return true;
            }
        }
        return false;
    }
}
