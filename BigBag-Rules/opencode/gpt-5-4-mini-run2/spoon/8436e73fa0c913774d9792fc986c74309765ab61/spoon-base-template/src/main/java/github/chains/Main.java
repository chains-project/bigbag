package github.chains;

import java.nio.file.Path;
import java.nio.file.Paths;
import spoon.Launcher;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

public class Main {
    private static final String OLD_PREFIX = "javax.jws";
    private static final String NEW_PREFIX = "jakarta.jws";

    public static void main(String[] args) {
        if (args.length < 2) {
            throw new IllegalArgumentException("Expected input and output source directories");
        }

        Path input = Paths.get(args[0]);
        Path output = Paths.get(args[1]);

        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.addInputResource(input.toString());
        launcher.setSourceOutputDirectory(output.toString());
        launcher.buildModel();

        for (CtTypeReference<?> typeReference : launcher.getModel().getElements(new TypeFilter<>(CtTypeReference.class))) {
            rewriteReference(typeReference);
        }

        launcher.prettyprint();
    }

    private static void rewriteReference(CtTypeReference<?> reference) {
        if (reference == null || reference.getQualifiedName() == null) {
            return;
        }

        String qualifiedName = reference.getQualifiedName();
        if (!qualifiedName.startsWith(OLD_PREFIX)) {
            return;
        }

        String newQualifiedName = NEW_PREFIX + qualifiedName.substring(OLD_PREFIX.length());
        int lastDot = newQualifiedName.lastIndexOf('.');
        if (lastDot > 0) {
            reference.setPackage(reference.getFactory().Package().createReference(newQualifiedName.substring(0, lastDot)));
            reference.setSimpleName(newQualifiedName.substring(lastDot + 1));
        } else {
            reference.setPackage(null);
            reference.setSimpleName(newQualifiedName);
        }
    }
}
