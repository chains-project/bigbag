package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.reference.CtPackageReference;
import spoon.reflect.reference.CtReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public class Main {
    private static final String OLD_PACKAGE_PREFIX = "javax.annotation";
    private static final String NEW_PACKAGE_PREFIX = "jakarta.annotation";

    public static void main(String[] args) {
        if (args.length == 0) {
            throw new IllegalArgumentException("Expected an input source directory");
        }

        String inputDirectory = args[0];
        String outputDirectory = args.length > 1 ? args[1] : inputDirectory;

        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        addJavaSources(launcher, Path.of(inputDirectory));
        launcher.buildModel();

        CtModel model = launcher.getModel();
        rewriteTypeReferences(model);
        rewritePackageReferences(model);
        rewriteImports(model);

        launcher.setSourceOutputDirectory(outputDirectory);
        launcher.prettyprint();
    }

    private static void addJavaSources(Launcher launcher, Path root) {
        try (Stream<Path> paths = Files.walk(root)) {
            paths.filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                    .filter(path -> path.toString().contains("/src/"))
                    .filter(path -> !path.toString().contains("/target/"))
                    .forEach(path -> launcher.addInputResource(path.toString()));
        } catch (IOException e) {
            throw new IllegalStateException("Failed to scan input sources", e);
        }
    }

    private static void rewriteTypeReferences(CtModel model) {
        List<CtTypeReference<?>> references = new ArrayList<>(
                model.getElements(new TypeFilter<>(CtTypeReference.class)));
        for (CtTypeReference<?> reference : references) {
            CtPackageReference packageReference = reference.getPackage();
            if (packageReference != null) {
                String qualifiedName = packageReference.getQualifiedName();
                if (qualifiedName != null && qualifiedName.startsWith(OLD_PACKAGE_PREFIX)) {
                    reference.setPackage(newPackageReference(reference, qualifiedName));
                }
            }
        }
    }

    private static void rewritePackageReferences(CtModel model) {
        List<CtPackageReference> packageReferences = new ArrayList<>(
                model.getElements(new TypeFilter<>(CtPackageReference.class)));
        for (CtPackageReference packageReference : packageReferences) {
            String qualifiedName = packageReference.getQualifiedName();
            if (qualifiedName != null && qualifiedName.startsWith(OLD_PACKAGE_PREFIX)) {
                packageReference.setSimpleName(rewritePackageName(qualifiedName));
            }
        }
    }

    private static void rewriteImports(CtModel model) {
        List<CtImport> imports = new ArrayList<>(model.getElements(new TypeFilter<>(CtImport.class)));
        for (CtImport ctImport : imports) {
            CtReference reference = ctImport.getReference();
            if (reference instanceof CtTypeReference<?> typeReference) {
                CtPackageReference packageReference = typeReference.getPackage();
                if (packageReference != null) {
                    String qualifiedName = packageReference.getQualifiedName();
                    if (qualifiedName != null && qualifiedName.startsWith(OLD_PACKAGE_PREFIX)) {
                        typeReference.setPackage(newPackageReference(typeReference, qualifiedName));
                    }
                }
            } else if (reference instanceof CtPackageReference packageReference) {
                String qualifiedName = packageReference.getQualifiedName();
                if (qualifiedName != null && qualifiedName.startsWith(OLD_PACKAGE_PREFIX)) {
                    packageReference.setSimpleName(rewritePackageName(qualifiedName));
                }
            }
        }
    }

    private static CtPackageReference newPackageReference(CtTypeReference<?> reference, String qualifiedName) {
        CtPackageReference packageReference = reference.getFactory().Core().createPackageReference();
        packageReference.setSimpleName(rewritePackageName(qualifiedName));
        return packageReference;
    }

    private static String rewritePackageName(String qualifiedName) {
        return NEW_PACKAGE_PREFIX + qualifiedName.substring(OLD_PACKAGE_PREFIX.length());
    }
}
