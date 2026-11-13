package github.chains;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import spoon.Launcher;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.reference.CtPackageReference;
import spoon.reflect.reference.CtReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

public class Main {
    private static final String OLD_PREFIX = "javax.validation";
    private static final String NEW_PREFIX = "jakarta.validation";

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            throw new IllegalArgumentException("Expected source directory argument");
        }

        Path sourceDir = Paths.get(args[0]);
        if (!Files.isDirectory(sourceDir)) {
            throw new IllegalArgumentException("Not a source directory: " + sourceDir);
        }

        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.addInputResource(sourceDir.toString());
        launcher.buildModel();
        launcher.setSourceOutputDirectory(sourceDir.toFile());

        launcher.getModel().getElements(new TypeFilter<>(CtImport.class)).forEach(ctImport -> rewriteReference(launcher, ctImport.getReference()));
        launcher.getModel().getElements(new TypeFilter<>(CtTypeReference.class)).forEach(typeReference -> rewriteReference(launcher, typeReference));

        launcher.prettyprint();
    }

    private static void rewriteReference(Launcher launcher, CtReference reference) {
        if (reference == null) {
            return;
        }
        if (!(reference instanceof CtTypeReference)) {
            return;
        }

        CtTypeReference<?> typeReference = (CtTypeReference<?>) reference;
        String qualifiedName = typeReference.getQualifiedName();
        if (qualifiedName == null || !qualifiedName.startsWith(OLD_PREFIX)) {
            return;
        }

        String replacementName = qualifiedName.replace(OLD_PREFIX, NEW_PREFIX);
        int packageEnd = replacementName.lastIndexOf('.');
        if (packageEnd < 0) {
            return;
        }

        CtPackageReference packageReference = launcher.getFactory().Package().createReference(replacementName.substring(0, packageEnd));
        typeReference.setPackage(packageReference);
        typeReference.setSimpleName(replacementName.substring(packageEnd + 1));
    }
}
