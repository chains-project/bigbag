package github.chains;

import spoon.Launcher;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtPackageReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.nio.file.Path;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.nio.file.Paths;
import java.io.IOException;

public class Main {
    private static final String OLD_VALIDATION_PACKAGE = "javax.validation";
    private static final String NEW_VALIDATION_PACKAGE = "jakarta.validation";

    public static void main(String[] args) {
        if (args.length == 0) {
            throw new IllegalArgumentException("Expected source directory path");
        }

        Path sourceDir = Paths.get(args[0]);

        Path mainSources = sourceDir.resolve("src/main/java");
        Path testSources = sourceDir.resolve("src/test/java");

        if (Files.isDirectory(mainSources) || Files.isDirectory(testSources)) {
            processSourceRoot(mainSources);
            processSourceRoot(testSources);
        } else {
            processSourceRoot(sourceDir);
        }
    }

    private static void processSourceRoot(Path sourceRoot) {
        if (!Files.isDirectory(sourceRoot)) {
            return;
        }

        Path outputRoot = Paths.get(sourceRoot.toString() + ".spoon-output");
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.addInputResource(sourceRoot.toString());
        launcher.setSourceOutputDirectory(outputRoot.toString());
        launcher.buildModel();

        Factory factory = launcher.getFactory();
        for (CtTypeReference<?> typeReference : launcher.getModel().getElements(new TypeFilter<>(CtTypeReference.class))) {
            rewriteTypeReference(typeReference, factory);
        }

        for (CtPackageReference packageReference : launcher.getModel().getElements(new TypeFilter<>(CtPackageReference.class))) {
            rewritePackageReference(packageReference, factory);
        }

        launcher.prettyprint();
        copyGeneratedSources(outputRoot, sourceRoot);
    }

    private static void copyGeneratedSources(Path outputRoot, Path sourceRoot) {
        if (!Files.isDirectory(outputRoot)) {
            return;
        }

        try {
            Files.walk(outputRoot)
                    .filter(path -> path.toString().endsWith(".java"))
                    .forEach(path -> {
                        Path relative = outputRoot.relativize(path);
                        Path target = sourceRoot.resolve(relative);
                        try {
                            if (target.getParent() != null) {
                                Files.createDirectories(target.getParent());
                            }
                            Files.copy(path, target, StandardCopyOption.REPLACE_EXISTING);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    });
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void rewriteTypeReference(CtTypeReference<?> reference, Factory factory) {
        if (reference == null) {
            return;
        }

        String qualifiedName = reference.getQualifiedName();
        if (qualifiedName == null || !qualifiedName.startsWith(OLD_VALIDATION_PACKAGE)) {
            return;
        }

        CtTypeReference<?> replacement = factory.Type().createReference(qualifiedName.replace(OLD_VALIDATION_PACKAGE, NEW_VALIDATION_PACKAGE));
        reference.replace(replacement);
    }

    private static void rewritePackageReference(CtPackageReference reference, Factory factory) {
        if (reference == null) {
            return;
        }

        String qualifiedName = reference.getQualifiedName();
        if (qualifiedName == null || !qualifiedName.startsWith(OLD_VALIDATION_PACKAGE)) {
            return;
        }

        CtPackageReference replacement = factory.Package().createReference(qualifiedName.replace(OLD_VALIDATION_PACKAGE, NEW_VALIDATION_PACKAGE));
        reference.replace(replacement);
    }
}
