package github.chains;

import java.io.File;
import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.reference.CtPackageReference;
import spoon.reflect.reference.CtReference;
import spoon.reflect.reference.CtTypeReference;

public class Main {
    private static final String OLD_PACKAGE_PREFIX = "com.google.api.services.translate";
    private static final String NEW_PACKAGE_PREFIX = "com.google.api.services.translate.v3";

    public static void main(String[] args) {
        if (args.length < 1) {
            throw new IllegalArgumentException("Usage: Main <inputSourceDir> [outputDir]");
        }

        File inputDir = new File(args[0]);
        File outputDir = args.length > 1 ? new File(args[1]) : new File(args[0] + "-spooned");

        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setIgnoreDuplicateDeclarations(true);
        launcher.addInputResource(inputDir.getAbsolutePath());
        launcher.setSourceOutputDirectory(outputDir);
        launcher.addProcessor(new AbstractProcessor<CtTypeReference<?>>() {
            @Override
            public void process(CtTypeReference<?> reference) {
                rewriteTypeReference(reference);
            }
        });
        launcher.addProcessor(new AbstractProcessor<CtImport>() {
            @Override
            public void process(CtImport ctImport) {
                CtReference reference = ctImport.getReference();
                if (reference instanceof CtTypeReference<?>) {
                    rewriteTypeReference((CtTypeReference<?>) reference);
                }
            }
        });
        launcher.run();
    }

    private static void rewriteTypeReference(CtTypeReference<?> reference) {
        CtPackageReference packageReference = reference.getPackage();
        if (packageReference == null) {
            return;
        }

        String qualifiedName = packageReference.getQualifiedName();
        if (!qualifiedName.equals(OLD_PACKAGE_PREFIX) && !qualifiedName.startsWith(OLD_PACKAGE_PREFIX + ".")) {
            return;
        }

        String rewrittenPackage = NEW_PACKAGE_PREFIX + qualifiedName.substring(OLD_PACKAGE_PREFIX.length());
        if (!rewrittenPackage.equals(qualifiedName)) {
            reference.setPackage(reference.getFactory().Package().createReference(rewrittenPackage));
        }
    }
}
