package github.chains;

import spoon.Launcher;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtExpression;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class Main {
    private static final String CONSTRUCTOR_TYPE = "org.yaml.snakeyaml.constructor.Constructor";
    private static final String LOADER_OPTIONS_TYPE = "org.yaml.snakeyaml.LoaderOptions";

    public static void main(String[] args) {
        if (args.length < 1) {
            throw new IllegalArgumentException("Usage: Main <input-source-dir> [output-source-dir]");
        }

        Path inputDir = Paths.get(args[0]);
        Path outputDir = args.length > 1 ? Paths.get(args[1]) : inputDir;

        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        addMavenJavaSources(launcher, inputDir);
        launcher.setSourceOutputDirectory(outputDir.toFile());
        launcher.buildModel();

        CtTypeReference<?> constructorType = launcher.getFactory().Type().createReference(CONSTRUCTOR_TYPE);
        CtTypeReference<?> fixType = launcher.getFactory().Type().createReference(LOADER_OPTIONS_TYPE);

        List<CtConstructorCall<?>> constructorCalls = launcher.getModel().getElements(new TypeFilter<>(CtConstructorCall.class));
        for (CtConstructorCall<?> call : constructorCalls) {
            if (isSnakeYamlConstructorWithoutLoaderOptions(call, constructorType)) {
                List<CtExpression<?>> callArguments = new ArrayList<>(call.getArguments());
                callArguments.add(launcher.getFactory().Code().createConstructorCall(fixType));
                call.replace(launcher.getFactory().Code().createConstructorCall(constructorType, callArguments.toArray(new CtExpression[0])));
            }
        }

        launcher.prettyprint();
    }

    private static void addMavenJavaSources(Launcher launcher, Path inputDir) {
        try {
            List<Path> sourceRoots = Files.walk(inputDir)
                    .filter(Main::isJavaSourceRoot)
                    .sorted()
                    .collect(Collectors.toList());
            if (sourceRoots.isEmpty()) {
                launcher.addInputResource(inputDir.toString());
                return;
            }
            for (Path sourceRoot : sourceRoots) {
                launcher.addInputResource(sourceRoot.toString());
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to discover Maven source roots under " + inputDir, e);
        }
    }

    private static boolean isJavaSourceRoot(Path path) {
        String normalized = path.toString().replace('\\', '/');
        return normalized.endsWith("/src/main/java") || normalized.endsWith("/src/test/java");
    }

    private static boolean isSnakeYamlConstructorWithoutLoaderOptions(CtConstructorCall<?> call, CtTypeReference<?> constructorType) {
        CtTypeReference<?> declaringType = call.getExecutable() == null ? null : call.getExecutable().getDeclaringType();
        if (declaringType == null || !constructorType.getQualifiedName().equals(declaringType.getQualifiedName())) {
            return false;
        }
        return call.getArguments().stream().noneMatch(arg -> arg.getType() != null && LOADER_OPTIONS_TYPE.equals(arg.getType().getQualifiedName()));
    }
}
