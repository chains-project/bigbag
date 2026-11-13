package github.chains;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.Files;
import java.util.List;
import java.util.stream.Stream;

import spoon.Launcher;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.reference.CtPackageReference;
import spoon.reflect.reference.CtReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

public class Main {
    private static final String OLD_PACKAGE = "org.mockito.runners";
    private static final String NEW_PACKAGE = "org.mockito.junit";

    public static void main(String[] args) {
        if (args.length != 1) {
            throw new IllegalArgumentException("Usage: Main <source-root>");
        }

        Path sourceRoot = Paths.get(args[0]).toAbsolutePath().normalize();

        for (Path sourceDir : findSourceDirectories(sourceRoot)) {
            Launcher launcher = new Launcher();
            launcher.getEnvironment().setNoClasspath(true);
            launcher.getEnvironment().setAutoImports(true);
            launcher.addInputResource(sourceDir.toString());
            launcher.setSourceOutputDirectory(sourceDir.toFile());

            launcher.buildModel();
            rewriteModel(launcher);
            launcher.prettyprint();
        }
    }

    private static List<Path> findSourceDirectories(Path root) {
        try (Stream<Path> stream = Files.walk(root)) {
            return stream.filter(Files::isDirectory)
                .filter(path -> path.getFileName() != null && path.getFileName().toString().equals("java"))
                .filter(path -> path.getParent() != null)
                .filter(path -> {
                    Path parent = path.getParent();
                    Path grandParent = parent.getParent();
                    return grandParent != null
                        && ("main".equals(parent.getFileName().toString())
                            || "test".equals(parent.getFileName().toString()));
                })
                .toList();
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Failed to scan source tree: " + root, e);
        }
    }

    private static void rewriteModel(Launcher launcher) {
        CtPackageReference newPackage = launcher.getFactory().Package().createReference(NEW_PACKAGE);

        for (CtTypeReference<?> reference : launcher.getModel().getElements(new TypeFilter<>(CtTypeReference.class))) {
            rewriteTypeReference(reference, newPackage);
        }

        for (CtImport ctImport : launcher.getModel().getElements(new TypeFilter<>(CtImport.class))) {
            CtReference reference = ctImport.getReference();
            if (reference instanceof CtTypeReference) {
                CtTypeReference<?> typeReference = (CtTypeReference<?>) reference;
                rewriteTypeReference(typeReference, newPackage);
            }
        }
    }

    private static void rewriteTypeReference(CtTypeReference<?> reference, CtPackageReference newPackage) {
        if (reference == null) {
            return;
        }

        String qualifiedName = reference.getQualifiedName();
        if (!qualifiedName.startsWith(OLD_PACKAGE + ".")) {
            return;
        }

        reference.setPackage(newPackage);
    }
}
