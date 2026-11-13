package github.chains;

import spoon.Launcher;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

public class Main {
    private static final Map<String, String> TYPE_RENAMES = Map.of(
            "eu.europa.esig.dss.pades.CertificationPermission",
            "eu.europa.esig.dss.enumerations.CertificationPermission"
    );

    public static void main(String[] args) {
        if (args.length < 1) {
            throw new IllegalArgumentException("Usage: Main <source-dir> [output-dir]");
        }

        Path sourceDir = Paths.get(args[0]);
        Path outputDir = args.length > 1
                ? Paths.get(args[1])
                : sourceDir.resolveSibling(sourceDir.getFileName() + "-transformed");

        try {
            Files.createDirectories(outputDir);
        } catch (Exception e) {
            throw new RuntimeException("Cannot create output directory: " + outputDir, e);
        }

        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(false);
        launcher.addInputResource(sourceDir.toString());
        launcher.setSourceOutputDirectory(outputDir.toFile());
        launcher.buildModel();

        for (Map.Entry<String, String> rename : TYPE_RENAMES.entrySet()) {
            renameTypeReferences(launcher, rename.getKey(), rename.getValue());
        }

        launcher.prettyprint();
    }

    private static void renameTypeReferences(Launcher launcher, String oldTypeName, String newTypeName) {
        CtTypeReference<?> replacement = launcher.getFactory().Type().createReference(newTypeName);
        for (CtTypeReference<?> reference : launcher.getModel().getElements(new TypeFilter<>(CtTypeReference.class))) {
            if (oldTypeName.equals(reference.getQualifiedName())) {
                reference.replace(replacement.clone());
            }
        }
    }
}
