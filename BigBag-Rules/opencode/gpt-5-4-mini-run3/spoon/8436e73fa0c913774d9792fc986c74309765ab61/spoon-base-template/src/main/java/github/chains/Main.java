package github.chains;

import java.util.ArrayList;
import java.util.List;
import spoon.Launcher;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

public class Main {
    private static final String OLD_PREFIX = "javax.jws";
    private static final String NEW_PREFIX = "jakarta.jws";

    public static void main(String[] args) {
        if (args.length < 1) {
            throw new IllegalArgumentException("Expected at least one input directory");
        }

        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setIgnoreDuplicateDeclarations(true);
        for (int i = 0; i < args.length - 1; i++) {
            launcher.addInputResource(args[i]);
        }
        String outputDirectory = args.length > 1 ? args[args.length - 1] : args[0] + "-rewritten";
        launcher.setSourceOutputDirectory(outputDirectory);
        launcher.buildModel();

        List<CtImport> imports = new ArrayList<>(launcher.getModel().getElements(new TypeFilter<>(CtImport.class)));
        for (CtImport ctImport : imports) {
            if (ctImport.getReference() instanceof CtTypeReference<?> typeReference) {
                String qualifiedName = typeReference.getQualifiedName();
                if (qualifiedName != null && qualifiedName.startsWith(OLD_PREFIX)) {
                    ctImport.setReference(typeReference.getFactory().Type().createReference(NEW_PREFIX + qualifiedName.substring(OLD_PREFIX.length())));
                }
            }
        }

        List<CtTypeReference<?>> typeReferences = new ArrayList<>(launcher.getModel().getElements(new TypeFilter<>(CtTypeReference.class)));
        for (CtTypeReference<?> typeReference : typeReferences) {
            rewriteReference(typeReference);
        }

        launcher.prettyprint();
    }

    private static void rewriteReference(CtTypeReference<?> reference) {
        if (reference == null) {
            return;
        }

        String qualifiedName = reference.getQualifiedName();
        if (qualifiedName == null || !qualifiedName.startsWith(OLD_PREFIX)) {
            return;
        }

        String replacement = NEW_PREFIX + qualifiedName.substring(OLD_PREFIX.length());
        CtTypeReference<?> newReference = reference.getFactory().Type().createReference(replacement);
        reference.replace(newReference);
    }
}
