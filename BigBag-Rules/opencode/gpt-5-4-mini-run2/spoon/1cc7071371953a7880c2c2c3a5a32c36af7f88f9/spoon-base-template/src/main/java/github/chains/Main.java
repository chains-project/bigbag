package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtFieldReference;
import spoon.reflect.reference.CtReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;

public class Main {
    private static final String OLD_PREFIX = "org.assertj.core.internal.bytebuddy";
    private static final String NEW_PREFIX = "net.bytebuddy";

    public static void main(String[] args) {
        if (args.length < 1) {
            throw new IllegalArgumentException("Usage: Main <input-source-directory> [output-source-directory]");
        }

        Path inputDirectory = Paths.get(args[0]);
        Path outputDirectory = args.length > 1
                ? Paths.get(args[1])
                : Paths.get(inputDirectory.toString() + "-fixed");

        try {
            Files.createDirectories(outputDirectory);
        } catch (Exception e) {
            throw new IllegalStateException("Could not create output directory " + outputDirectory, e);
        }

        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(false);
        launcher.addInputResource(inputDirectory.toString());
        launcher.setSourceOutputDirectory(outputDirectory.toFile());

        launcher.buildModel();
        CtModel model = launcher.getModel();

        rewriteImports(model);
        rewriteTypeReferences(model);
        rewriteExecutableReferences(model);
        rewriteFieldReferences(model);

        launcher.prettyprint();
    }

    private static void rewriteImports(CtModel model) {
        Collection<CtImport> imports = model.getRootPackage().getElements(new TypeFilter<>(CtImport.class));
        for (CtImport ctImport : imports) {
            CtReference reference = ctImport.getReference();
            if (reference != null) {
                rewriteReference(reference);
            }
        }
    }

    private static void rewriteTypeReferences(CtModel model) {
        Collection<CtTypeReference> references = model.getRootPackage().getElements(new TypeFilter<>(CtTypeReference.class));
        for (CtTypeReference<?> reference : references) {
            rewriteReference(reference);
        }
    }

    private static void rewriteExecutableReferences(CtModel model) {
        Collection<CtExecutableReference> references = model.getRootPackage().getElements(new TypeFilter<>(CtExecutableReference.class));
        for (CtExecutableReference<?> reference : references) {
            rewriteExecutableReference(reference);
        }
    }

    private static void rewriteFieldReferences(CtModel model) {
        Collection<CtFieldReference> references = model.getRootPackage().getElements(new TypeFilter<>(CtFieldReference.class));
        for (CtFieldReference<?> reference : references) {
            rewriteFieldReference(reference);
        }
    }

    private static void rewriteReference(CtReference reference) {
        if (reference instanceof CtTypeReference<?>) {
            rewriteTypeReference((CtTypeReference<?>) reference);
        } else if (reference instanceof CtExecutableReference<?>) {
            rewriteExecutableReference((CtExecutableReference<?>) reference);
        } else if (reference instanceof CtFieldReference<?>) {
            rewriteFieldReference((CtFieldReference<?>) reference);
        }
    }

    private static void rewriteTypeReference(CtTypeReference<?> reference) {
        String qualifiedName = reference.getQualifiedName();
        if (!qualifiedName.startsWith(OLD_PREFIX)) {
            return;
        }

        CtTypeReference<?> replacement = reference.getFactory().Type().createReference(rewriteQualifiedName(qualifiedName));
        reference.replace(replacement);
    }

    private static void rewriteExecutableReference(CtExecutableReference<?> reference) {
        CtTypeReference<?> declaringType = reference.getDeclaringType();
        if (declaringType == null) {
            return;
        }

        String qualifiedName = declaringType.getQualifiedName();
        if (!qualifiedName.startsWith(OLD_PREFIX)) {
            return;
        }

        reference.setDeclaringType(declaringType.getFactory().Type().createReference(rewriteQualifiedName(qualifiedName)));
    }

    private static void rewriteFieldReference(CtFieldReference<?> reference) {
        CtTypeReference<?> declaringType = reference.getDeclaringType();
        if (declaringType == null) {
            return;
        }

        String qualifiedName = declaringType.getQualifiedName();
        if (!qualifiedName.startsWith(OLD_PREFIX)) {
            return;
        }

        reference.setDeclaringType(declaringType.getFactory().Type().createReference(rewriteQualifiedName(qualifiedName)));
    }

    private static String rewriteQualifiedName(String qualifiedName) {
        return NEW_PREFIX + qualifiedName.substring(OLD_PREFIX.length());
    }
}
