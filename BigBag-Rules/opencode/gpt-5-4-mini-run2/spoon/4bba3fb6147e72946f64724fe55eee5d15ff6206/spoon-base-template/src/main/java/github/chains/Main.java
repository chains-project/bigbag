package github.chains;

import spoon.Launcher;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.reference.CtReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class Main {
    public static void main(String[] args) {
        if (args.length < 2) {
            throw new IllegalArgumentException("Usage: <input-source-dir> <output-source-dir>");
        }

        Path inputDir = Paths.get(args[0]).toAbsolutePath().normalize();
        Path outputDir = Paths.get(args[1]).toAbsolutePath().normalize();

        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.addInputResource(inputDir.toString());
        launcher.buildModel();

        List<CtTypeReference<?>> typeReferences = launcher.getModel().getElements(new TypeFilter<>(CtTypeReference.class));
        for (CtTypeReference<?> typeReference : typeReferences) {
            remapJakartaAnnotationTypes(typeReference, launcher);
        }

        launcher.setSourceOutputDirectory(outputDir.toString());
        launcher.prettyprint();
    }

    private static void remapJakartaAnnotationTypes(CtTypeReference<?> typeReference, Launcher launcher) {
        String qualifiedName = typeReference.getQualifiedName();
        String replacement = remapQualifiedName(qualifiedName);
        if (replacement == null || replacement.equals(qualifiedName)) {
            return;
        }

        CtTypeReference<?> newReference = launcher.getFactory().Type().createReference(replacement);
        copyReferenceState(typeReference, newReference);
        typeReference.replace(newReference);
    }

    private static void copyReferenceState(CtTypeReference<?> source, CtTypeReference<?> target) {
        if (source instanceof CtReference) {
            target.setComments(((CtReference) source).getComments());
        }
    }

    private static String remapQualifiedName(String qualifiedName) {
        if (qualifiedName.startsWith("javax.annotation.security.")) {
            return "jakarta.annotation.security." + qualifiedName.substring("javax.annotation.security.".length());
        }
        if (qualifiedName.startsWith("javax.annotation.sql.")) {
            return "jakarta.annotation.sql." + qualifiedName.substring("javax.annotation.sql.".length());
        }
        if (qualifiedName.startsWith("javax.annotation.")) {
            return "jakarta.annotation." + qualifiedName.substring("javax.annotation.".length());
        }
        return null;
    }
}
