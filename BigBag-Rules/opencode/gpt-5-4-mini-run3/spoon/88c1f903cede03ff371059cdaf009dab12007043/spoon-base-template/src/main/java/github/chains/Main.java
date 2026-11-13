package github.chains;

import spoon.Launcher;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.nio.file.Path;
import java.nio.file.Paths;

public class Main {

    private static final String OLD_ZIPFILE_FQN = "net.lingala.zip4j.core.ZipFile";

    private static final String NEW_ZIPFILE_FQN = "net.lingala.zip4j.ZipFile";

    public static void main(final String[] args) {
        if (args.length < 1 || args.length > 2) {
            throw new IllegalArgumentException("Usage: Main <source-dir> [output-dir]");
        }

        final Path sourceDir = Paths.get(args[0]).toAbsolutePath().normalize();
        final Path outputDir = args.length == 2
                ? Paths.get(args[1]).toAbsolutePath().normalize()
                : sourceDir;

        final Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.addInputResource(sourceDir.toString());
        launcher.setSourceOutputDirectory(outputDir.toFile());
        launcher.buildModel();

        rewriteZipFileReferences(launcher);
        launcher.prettyprint();
    }

    private static void rewriteZipFileReferences(final Launcher launcher) {
        for (CtImport ctImport : launcher.getModel().getElements(new TypeFilter<>(CtImport.class))) {
            final var reference = ctImport.getReference();
            if (reference instanceof CtTypeReference<?> typeReference
                    && OLD_ZIPFILE_FQN.equals(typeReference.getQualifiedName())) {
                ctImport.setReference(launcher.getFactory().createReference(NEW_ZIPFILE_FQN));
            }
        }

        for (CtTypeReference<?> typeReference
                : launcher.getModel().getElements(new TypeFilter<>(CtTypeReference.class))) {
            if (typeReference.getParent(CtImport.class) != null) {
                continue;
            }

            if (OLD_ZIPFILE_FQN.equals(typeReference.getQualifiedName())) {
                typeReference.replace(launcher.getFactory().createReference(NEW_ZIPFILE_FQN));
            }
        }
    }
}
