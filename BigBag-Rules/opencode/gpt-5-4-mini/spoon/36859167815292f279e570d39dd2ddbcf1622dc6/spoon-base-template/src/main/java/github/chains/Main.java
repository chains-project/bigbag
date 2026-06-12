package github.chains;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.stream.Stream;
import java.util.List;
import spoon.Launcher;
import spoon.OutputType;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.visitor.filter.TypeFilter;

public class Main {
    private static final String FOP_FACTORY = "org.apache.fop.apps.FopFactory";
    private static final String NEW_INSTANCE = "newInstance";

    public static void main(String[] args) {
        if (args.length < 1) {
            throw new IllegalArgumentException("Usage: Main <source-dir>");
        }

        Path sourceDir = Paths.get(args[0]).toAbsolutePath().normalize();
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        Path outputDir;
        try {
            outputDir = Files.createTempDirectory("spoon-output");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        launcher.getEnvironment().setSourceOutputDirectory(outputDir.toFile());
        launcher.getEnvironment().setOutputType(OutputType.CLASSES);
        List<Path> sourceRoots = addSourceRoots(launcher, sourceDir);
        launcher.buildModel();

        Factory factory = launcher.getFactory();
        List<CtInvocation<?>> invocations = factory.getModel().getElements(new TypeFilter<>(CtInvocation.class));
        for (CtInvocation<?> invocation : invocations) {
            if (!isOldFopFactoryNewInstance(invocation)) {
                continue;
            }

            invocation.addArgument(factory.Code().createCodeSnippetExpression("java.nio.file.Paths.get(\"\").toAbsolutePath().toUri()"));
        }

        launcher.prettyprint();
        copyJavaSources(outputDir, sourceRoots);
    }

    private static boolean isOldFopFactoryNewInstance(CtInvocation<?> invocation) {
        CtExecutableReference<?> executable = invocation.getExecutable();
        return executable != null
            && NEW_INSTANCE.equals(executable.getSimpleName())
            && executable.getParameters().isEmpty()
            && executable.getDeclaringType() != null
            && FOP_FACTORY.equals(executable.getDeclaringType().getQualifiedName());
    }

    private static void copyJavaSources(Path outputDir, List<Path> sourceRoots) {
        if (!Files.exists(outputDir)) {
            return;
        }
        try {
            Files.walk(outputDir)
                .filter(path -> path.toString().endsWith(".java"))
                .forEach(path -> {
                    Path relative = outputDir.relativize(path);
                    Path target = resolveTarget(sourceRoots, relative);
                    try {
                        Files.createDirectories(target.getParent());
                        Files.copy(path, target, StandardCopyOption.REPLACE_EXISTING);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });
            Files.walk(outputDir)
                .sorted(Comparator.reverseOrder())
                .forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static Path resolveTarget(List<Path> sourceRoots, Path relative) {
        for (Path root : sourceRoots) {
            Path candidate = root.resolve(relative);
            if (Files.exists(candidate)) {
                return candidate;
            }
        }
        if (sourceRoots.isEmpty()) {
            throw new IllegalStateException("No source roots found");
        }
        return sourceRoots.get(0).resolve(relative);
    }

    private static List<Path> addSourceRoots(Launcher launcher, Path sourceDir) {
        List<Path> sourceRoots = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(sourceDir)) {
            paths.filter(path -> Files.isDirectory(path)
                    && path.endsWith(Paths.get("src", "main", "java")))
                .forEach(path -> {
                    launcher.addInputResource(path.toString());
                    sourceRoots.add(path);
                });
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return sourceRoots;
    }

}
