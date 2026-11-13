package github.chains;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

import spoon.Launcher;
import spoon.reflect.reference.CtPackageReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

public class Main {
    private static final String OLD_PREFIX = "javax.validation";
    private static final String NEW_PREFIX = "jakarta.validation";

    public static void main(String[] args) {
        if (args.length < 1) {
            throw new IllegalArgumentException("Usage: Main <input-source-dir> [output-dir]");
        }

        Path inputDir = Paths.get(args[0]);
        Path outputDir = args.length >= 2 ? Paths.get(args[1]) : inputDir.resolveSibling(inputDir.getFileName() + "-out");

        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(false);
        launcher.addInputResource(inputDir.toString());
        launcher.setSourceOutputDirectory(outputDir.toFile());
        launcher.buildModel();

        launcher.getModel().getElements(new TypeFilter<>(CtTypeReference.class))
                .forEach(Main::rewriteTypeReference);
        launcher.getModel().getElements(new TypeFilter<>(CtPackageReference.class))
                .forEach(Main::rewritePackageReference);

        launcher.prettyprint();
    }

    private static void rewriteTypeReference(CtTypeReference<?> reference) {
        CtPackageReference packageReference = reference.getPackage();
        if (packageReference == null) {
            return;
        }

        String packageName = packageReference.getQualifiedName();
        if (!packageName.startsWith(OLD_PREFIX)) {
            return;
        }

        reference.setPackage(reference.getFactory().Package().createReference(rewriteQualifiedName(packageName)));
    }

    private static void rewritePackageReference(CtPackageReference reference) {
        String packageName = reference.getQualifiedName();
        if (!packageName.startsWith(OLD_PREFIX)) {
            return;
        }

        reference.setSimpleName(rewriteQualifiedName(packageName));
    }

    private static String rewriteQualifiedName(String name) {
        return NEW_PREFIX + name.substring(OLD_PREFIX.length());
    }
}
