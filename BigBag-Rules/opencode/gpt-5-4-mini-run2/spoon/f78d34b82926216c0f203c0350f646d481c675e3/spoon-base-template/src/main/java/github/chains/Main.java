package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtFieldReference;
import spoon.reflect.reference.CtPackageReference;
import spoon.reflect.reference.CtReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.nio.file.Path;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;
import java.io.IOException;
import java.util.stream.Stream;

public class Main {
    private static final String OLD_PACKAGE = "develop.p2p.lib";
    private static final String NEW_PACKAGE = "tokyo.peya.lib";

    public static void main(String[] args) {
        if (args.length < 2) {
            throw new IllegalArgumentException("Expected input and output directories");
        }

        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(false);
        launcher.getEnvironment().setCommentEnabled(true);
        launcher.addInputResource(args[0]);

        Path output = Paths.get(args[1]);
        launcher.setSourceOutputDirectory(output.toFile());

        launcher.buildModel();
        rewriteBrokenApiReferences(launcher.getModel(), launcher);
        launcher.prettyprint();
        postProcessWrittenSources(output);
    }

    private static void postProcessWrittenSources(Path outputRoot) {
        try (Stream<Path> paths = Files.walk(outputRoot)) {
            paths.filter(path -> path.toString().endsWith(".java")).forEach(Main::rewriteFileText);
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void rewriteFileText(Path path) {
        try {
            String content = Files.readString(path, StandardCharsets.UTF_8);
            String rewritten = content.replace(OLD_PACKAGE, NEW_PACKAGE);
            if (!content.equals(rewritten)) {
                Files.writeString(path, rewritten, StandardCharsets.UTF_8);
            }
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void rewriteBrokenApiReferences(CtModel model, Launcher launcher) {
        for (CtImport ctImport : model.getElements(new TypeFilter<>(CtImport.class))) {
            CtReference reference = ctImport.getReference();
            if (reference != null) {
                ctImport.setReference(rewriteReference(reference, launcher));
            }
        }

        for (CtTypeReference<?> typeReference : model.getElements(new TypeFilter<>(CtTypeReference.class))) {
            if (matches(typeReference.getQualifiedName())) {
                typeReference.replace(launcher.getFactory().Type().createReference(rewriteQualifiedName(typeReference.getQualifiedName())));
            }
        }

        for (CtExecutableReference<?> executableReference : model.getElements(new TypeFilter<>(CtExecutableReference.class))) {
            CtTypeReference<?> declaringType = executableReference.getDeclaringType();
            if (declaringType != null && matches(declaringType.getQualifiedName())) {
                executableReference.setDeclaringType(launcher.getFactory().Type().createReference(rewriteQualifiedName(declaringType.getQualifiedName())));
            }
        }

        for (CtFieldReference<?> fieldReference : model.getElements(new TypeFilter<>(CtFieldReference.class))) {
            CtTypeReference<?> declaringType = fieldReference.getDeclaringType();
            if (declaringType != null && matches(declaringType.getQualifiedName())) {
                fieldReference.setDeclaringType(launcher.getFactory().Type().createReference(rewriteQualifiedName(declaringType.getQualifiedName())));
            }
        }

        for (CtPackageReference packageReference : model.getElements(new TypeFilter<>(CtPackageReference.class))) {
            if (matches(packageReference.getQualifiedName())) {
                packageReference.replace(launcher.getFactory().Package().createReference(rewriteQualifiedName(packageReference.getQualifiedName())));
            }
        }
    }

    private static CtReference rewriteReference(CtReference reference, Launcher launcher) {
        if (reference instanceof CtTypeReference) {
            CtTypeReference<?> typeReference = (CtTypeReference<?>) reference;
            if (matches(typeReference.getQualifiedName())) {
                return launcher.getFactory().Type().createReference(rewriteQualifiedName(typeReference.getQualifiedName()));
            }
            return reference;
        }

        if (reference instanceof CtExecutableReference) {
            CtExecutableReference<?> executableReference = (CtExecutableReference<?>) reference;
            CtTypeReference<?> declaringType = executableReference.getDeclaringType();
            if (declaringType != null && matches(declaringType.getQualifiedName())) {
                executableReference.setDeclaringType(launcher.getFactory().Type().createReference(rewriteQualifiedName(declaringType.getQualifiedName())));
            }
            return executableReference;
        }

        if (reference instanceof CtFieldReference) {
            CtFieldReference<?> fieldReference = (CtFieldReference<?>) reference;
            CtTypeReference<?> declaringType = fieldReference.getDeclaringType();
            if (declaringType != null && matches(declaringType.getQualifiedName())) {
                fieldReference.setDeclaringType(launcher.getFactory().Type().createReference(rewriteQualifiedName(declaringType.getQualifiedName())));
            }
            return fieldReference;
        }

        if (reference instanceof CtPackageReference) {
            CtPackageReference packageReference = (CtPackageReference) reference;
            if (matches(packageReference.getQualifiedName())) {
                return launcher.getFactory().Package().createReference(rewriteQualifiedName(packageReference.getQualifiedName()));
            }
        }

        return reference;
    }

    private static boolean matches(String qualifiedName) {
        return qualifiedName != null && (qualifiedName.equals(OLD_PACKAGE) || qualifiedName.startsWith(OLD_PACKAGE + "."));
    }

    private static String rewriteQualifiedName(String qualifiedName) {
        if (qualifiedName == null || !matches(qualifiedName)) {
            return qualifiedName;
        }
        return NEW_PACKAGE + qualifiedName.substring(OLD_PACKAGE.length());
    }
}
