package github.chains;

import spoon.Launcher;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.declaration.CtType;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtTypeReference;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

public class Main {
    private static final String SNAKEYAML_CONSTRUCTOR = "org.yaml.snakeyaml.constructor.Constructor";
    private static final String SNAKEYAML_LOADER_OPTIONS = "org.yaml.snakeyaml.LoaderOptions";

    public static void main(String[] args) {
        if (args.length < 1) {
            throw new IllegalArgumentException("Usage: Main <input-source-dir> [output-source-dir]");
        }

        String inputDir = args[0];
        String outputDir = args.length > 1 ? args[1] : inputDir + "-transformed";

        Launcher launcher = new Launcher();
        for (String sourceFile : listJavaFiles(inputDir)) {
            launcher.addInputResource(sourceFile);
        }
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setIgnoreSyntaxErrors(true);
        launcher.buildModel();

        Factory factory = launcher.getFactory();
        CtTypeReference<?> constructorType = factory.Type().createReference(SNAKEYAML_CONSTRUCTOR);
        CtTypeReference<?> loaderOptionsType = factory.Type().createReference(SNAKEYAML_LOADER_OPTIONS);
        for (CtType<?> type : launcher.getModel().getAllTypes()) {
            for (CtConstructorCall<?> call : type.getElements(e -> e instanceof CtConstructorCall<?>).stream()
                    .map(e -> (CtConstructorCall<?>) e)
                    .toList()) {
                if (!isSnakeYamlConstructorCall(call, constructorType)) {
                    continue;
                }
                if (call.getArguments().size() != 1) {
                    continue;
                }

                CtConstructorCall<?> loaderOptionsCall = factory.Code().createConstructorCall(loaderOptionsType);
                call.addArgument(loaderOptionsCall);
            }
        }

        launcher.setSourceOutputDirectory(outputDir);
        launcher.prettyprint();
    }

    private static List<String> listJavaFiles(String inputDir) {
        try {
            return Files.walk(Paths.get(inputDir))
                    .filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                    .filter(path -> !path.toString().contains("/.git/"))
                    .filter(path -> !path.toString().contains("/target/"))
                    .map(Path::toString)
                    .collect(Collectors.toList());
        } catch (IOException e) {
            throw new RuntimeException("Failed to scan Java sources under " + inputDir, e);
        }
    }

    private static boolean isSnakeYamlConstructorCall(CtConstructorCall<?> call, CtTypeReference<?> constructorType) {
        CtExecutableReference<?> executable = call.getExecutable();
        return executable != null
                && executable.getDeclaringType() != null
                && executable.getDeclaringType().getQualifiedName().equals(constructorType.getQualifiedName())
                && executable.isConstructor();
    }
}
