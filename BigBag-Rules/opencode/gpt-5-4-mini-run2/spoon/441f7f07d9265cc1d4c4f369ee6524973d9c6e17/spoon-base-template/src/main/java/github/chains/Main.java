package github.chains;

import java.nio.file.Path;

import spoon.Launcher;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.reference.CtPackageReference;
import spoon.reflect.reference.CtReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

public class Main {
    private static final String OLD_PREFIX = "com.gargoylesoftware.htmlunit";
    private static final String NEW_PREFIX = "org.htmlunit";

    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: Main <input-source-dir> <output-source-dir>");
            System.exit(1);
        }

        Launcher launcher = new Launcher();
        launcher.addInputResource(Path.of(args[0]).toString());
        launcher.setSourceOutputDirectory(Path.of(args[1]).toString());
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);

        launcher.buildModel();
        rewriteHtmlUnitReferences(launcher);
        launcher.prettyprint();
    }

    private static void rewriteHtmlUnitReferences(Launcher launcher) {
        CtPackageReference newPackage = launcher.getFactory().createPackageReference().setSimpleName(NEW_PREFIX);

        for (CtTypeReference<?> typeReference : launcher.getModel().getElements(new TypeFilter<>(CtTypeReference.class))) {
            rewriteReference(typeReference, newPackage);
        }

        for (CtImport ctImport : launcher.getModel().getElements(new TypeFilter<>(CtImport.class))) {
            CtReference reference = ctImport.getReference();
            if (reference instanceof CtTypeReference<?> typeReference) {
                rewriteReference(typeReference, newPackage);
                ctImport.setReference(typeReference);
            }
        }
    }

    private static void rewriteReference(CtTypeReference<?> reference, CtPackageReference newPackage) {
        String qualifiedName = reference.getQualifiedName();
        if (qualifiedName != null && qualifiedName.startsWith(OLD_PREFIX + ".")) {
            reference.setPackage(newPackage);
        }
    }
}
