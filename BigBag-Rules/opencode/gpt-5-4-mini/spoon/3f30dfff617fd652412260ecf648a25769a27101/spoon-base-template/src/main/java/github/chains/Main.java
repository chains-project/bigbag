package github.chains;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.declaration.CtCompilationUnit;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.reference.CtPackageReference;
import spoon.reflect.reference.CtReference;
import spoon.reflect.reference.CtTypeReference;

import java.io.File;

public class Main {
    private static final String OLD_PACKAGE = "javax.interceptor";
    private static final String NEW_PACKAGE = "jakarta.interceptor";

    public static void main(String[] args) {
        if (args.length < 1) {
            throw new IllegalArgumentException("Usage: <input-source-dir> [output-source-dir]");
        }

        String inputSourceDir = args[0];
        String outputSourceDir = args.length > 1 ? args[1] : inputSourceDir + "-spooned";

        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(false);
        launcher.addInputResource(inputSourceDir);
        launcher.setSourceOutputDirectory(new File(outputSourceDir));
        launcher.addProcessor(new AbstractProcessor<CtTypeReference<?>>() {
            @Override
            public void process(CtTypeReference<?> reference) {
                rewritePackage(reference);
            }
        });
        launcher.addProcessor(new AbstractProcessor<CtImport>() {
            @Override
            public void process(CtImport ctImport) {
                if (ctImport.getReference() instanceof CtTypeReference<?>) {
                    CtTypeReference<?> typeReference = (CtTypeReference<?>) ctImport.getReference();
                    if (isOldPackageType(typeReference)) {
                        ctImport.delete();
                    }
                }
            }
        });
        launcher.addProcessor(new AbstractProcessor<CtCompilationUnit>() {
            @Override
            public void process(CtCompilationUnit unit) {
                unit.getImports().removeIf(importEntry -> isOldPackageImport(importEntry.getReference()));
            }
        });
        launcher.run();
    }

    private static void rewritePackage(CtTypeReference<?> reference) {
        if (reference == null || reference.getPackage() == null) {
            return;
        }
        if (OLD_PACKAGE.equals(reference.getPackage().getQualifiedName())) {
            reference.setPackage(reference.getFactory().Package().createReference(NEW_PACKAGE));
        }
    }

    private static boolean isOldPackageImport(CtReference reference) {
        return reference instanceof CtPackageReference
                && OLD_PACKAGE.equals(((CtPackageReference) reference).getQualifiedName());
    }

    private static boolean isOldPackageType(CtTypeReference<?> typeReference) {
        return typeReference.getPackage() != null
                && OLD_PACKAGE.equals(typeReference.getPackage().getQualifiedName());
    }
}
