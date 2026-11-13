package github.chains;

import spoon.Launcher;
import spoon.reflect.declaration.CtCompilationUnit;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.ArrayList;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.Files;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Comparator;

public class Main {

    private static final String OLD_TYPE = "net.lingala.zip4j.core.ZipFile";

    private static final String NEW_TYPE = "net.lingala.zip4j.ZipFile";

    public static void main(final String[] args) {
        if (args.length < 1 || args.length > 2) {
            throw new IllegalArgumentException("Usage: Main <source-dir> [output-dir]");
        }

        final Path sourceDir = Paths.get(args[0]).toAbsolutePath().normalize();
        final Path outputDir = args.length == 2
                ? Paths.get(args[1]).toAbsolutePath().normalize()
                : Paths.get(System.getProperty("java.io.tmpdir"),
                        "spoon-out-" + sourceDir.getFileName()).toAbsolutePath().normalize();

        final Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.addInputResource(sourceDir.toString());
        launcher.setSourceOutputDirectory(outputDir.toFile());
        launcher.buildModel();

        final Factory factory = launcher.getFactory();
        final CtTypeReference<?> newType = factory.Type().createReference(NEW_TYPE);

        for (CtTypeReference<?> reference : launcher.getModel().getElements(new TypeFilter<>(CtTypeReference.class))) {
            if (OLD_TYPE.equals(reference.getQualifiedName())) {
                reference.replace(newType.clone());
            }
        }

        for (CtCompilationUnit compilationUnit : launcher.getModel().getElements(
                new TypeFilter<>(CtCompilationUnit.class))) {
            final var updatedImports = new ArrayList<>(compilationUnit.getImports());
            boolean changed = false;
            for (int i = 0; i < updatedImports.size(); i++) {
                final var importReference = updatedImports.get(i).getReference();
                if (importReference instanceof CtTypeReference<?> typeReference
                        && OLD_TYPE.equals(typeReference.getQualifiedName())) {
                    updatedImports.set(i, factory.Type().createImport(newType.clone()));
                    changed = true;
                }
            }
            if (changed) {
                compilationUnit.setImports(updatedImports);
            }
        }

        launcher.prettyprint();

        try {
            Files.walk(outputDir)
                    .filter(Files::isRegularFile)
                    .forEach(path -> {
                        final Path relative = outputDir.relativize(path);
                        final Path target = sourceDir.resolve(relative);
                        try {
                            Files.createDirectories(target.getParent());
                            Files.copy(path, target, StandardCopyOption.REPLACE_EXISTING);
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    });
            Files.walk(outputDir)
                    .sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    });
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
