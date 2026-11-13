package github.chains;

import spoon.Launcher;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtPackageReference;
import spoon.reflect.reference.CtReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class Main {
    private static final String OLD_PREFIX = "javax.validation";
    private static final String NEW_PREFIX = "jakarta.validation";

    public static void main(String[] args) {
        if (args.length < 1) {
            throw new IllegalArgumentException("Usage: Main <input-source-dir> [output-source-dir]");
        }

        Path inputDir = Paths.get(args[0]).toAbsolutePath();
        Path outputDir = args.length > 1 ? Paths.get(args[1]).toAbsolutePath() : inputDir;

        Launcher launcher = new Launcher();
        launcher.getEnvironment().setComplianceLevel(11);
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.addInputResource(inputDir.toString());
        launcher.setSourceOutputDirectory(outputDir.toFile());
        launcher.buildModel();

        rewriteValidationReferences(launcher.getFactory());
        launcher.prettyprint();
    }

    private static void rewriteValidationReferences(Factory factory) {
        List<CtTypeReference> typeReferences = factory.getModel().getElements(new TypeFilter<>(CtTypeReference.class));
        for (CtTypeReference<?> reference : typeReferences) {
            CtTypeReference<?> updated = rewriteTypeReference(reference, factory);
            if (updated != null) {
                reference.replace(updated);
            }
        }

        List<CtPackageReference> packageReferences = factory.getModel().getElements(new TypeFilter<>(CtPackageReference.class));
        for (CtPackageReference reference : packageReferences) {
            CtPackageReference updated = rewritePackageReference(reference, factory);
            if (updated != null) {
                reference.replace(updated);
            }
        }

        List<CtImport> imports = factory.getModel().getElements(new TypeFilter<>(CtImport.class));
        for (CtImport ctImport : imports) {
            CtReference reference = ctImport.getReference();
            CtReference updated = rewriteReference(reference, factory);
            if (updated != null) {
                ctImport.replace(factory.createImport(updated));
            }
        }
    }

    private static CtReference rewriteReference(CtReference reference, Factory factory) {
        if (reference instanceof CtTypeReference) {
            return rewriteTypeReference((CtTypeReference<?>) reference, factory);
        }
        if (reference instanceof CtPackageReference) {
            return rewritePackageReference((CtPackageReference) reference, factory);
        }
        return null;
    }

    private static CtTypeReference<?> rewriteTypeReference(CtTypeReference<?> reference, Factory factory) {
        String qualifiedName = reference.getQualifiedName();
        if (!qualifiedName.startsWith(OLD_PREFIX)) {
            return null;
        }

        CtTypeReference<?> updated = reference.clone();
        CtPackageReference packageReference = reference.getPackage();
        if (packageReference != null) {
            updated.setPackage(factory.Package().createReference(rewritePrefix(packageReference.getQualifiedName())));
        }
        return updated;
    }

    private static CtPackageReference rewritePackageReference(CtPackageReference reference, Factory factory) {
        String qualifiedName = reference.getQualifiedName();
        if (!qualifiedName.startsWith(OLD_PREFIX)) {
            return null;
        }

        CtPackageReference updated = reference.clone();
        updated.setSimpleName(rewritePrefix(qualifiedName));
        return updated;
    }

    private static String rewritePrefix(String qualifiedName) {
        return NEW_PREFIX + qualifiedName.substring(OLD_PREFIX.length());
    }
}
