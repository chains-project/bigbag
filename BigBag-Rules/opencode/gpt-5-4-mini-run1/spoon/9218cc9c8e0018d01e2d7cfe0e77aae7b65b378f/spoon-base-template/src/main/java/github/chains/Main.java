package github.chains;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.declaration.CtImportKind;
import spoon.reflect.reference.CtPackageReference;
import spoon.reflect.reference.CtReference;
import spoon.reflect.reference.CtTypeReference;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Main {
    private static final String OLD_PREFIX = "javax.validation";
    private static final String NEW_PREFIX = "jakarta.validation";

    public static void main(String[] args) {
        if (args.length < 1 || args.length > 2) {
            throw new IllegalArgumentException("Usage: Main <project-root-or-source-dir> [output-dir]");
        }

        Path input = Paths.get(args[0]).toAbsolutePath().normalize();
        Path output = args.length > 1
                ? Paths.get(args[1]).toAbsolutePath().normalize()
                : input.resolveSibling(input.getFileName() + "-transformed");

        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(true);
        launcher.getEnvironment().setComplianceLevel(11);

        addSources(launcher, input);
        launcher.setSourceOutputDirectory(output.toFile());
        launcher.addProcessor(new AbstractProcessor<CtImport>() {
            @Override
            public void process(CtImport ctImport) {
                CtReference reference = ctImport.getReference();
                if (reference == null) {
                    return;
                }
                String qualifiedName = reference.getQualifiedName();
                if (qualifiedName == null || !qualifiedName.startsWith(OLD_PREFIX)) {
                    return;
                }
                ctImport.setReference(rewriteReference(reference));
            }
        });

        launcher.run();
    }

    private static void addSources(Launcher launcher, Path input) {
        Path mainJava = input.resolve("src/main/java");
        Path testJava = input.resolve("src/test/java");
        if (Files.isDirectory(mainJava) || Files.isDirectory(testJava)) {
            if (Files.isDirectory(mainJava)) {
                launcher.addInputResource(mainJava.toString());
            }
            if (Files.isDirectory(testJava)) {
                launcher.addInputResource(testJava.toString());
            }
        } else {
            launcher.addInputResource(input.toString());
        }
    }

    private static CtReference rewriteReference(CtReference reference) {
        if (reference instanceof CtTypeReference) {
            CtTypeReference<?> typeReference = (CtTypeReference<?>) reference;
            return typeReference.getFactory().Type().createReference(toJakarta(typeReference.getQualifiedName()));
        }
        if (reference instanceof CtPackageReference) {
            CtPackageReference packageReference = (CtPackageReference) reference;
            return packageReference.getFactory().Package().createReference(toJakarta(packageReference.getQualifiedName()));
        }
        throw new IllegalArgumentException("Unsupported import reference type: " + reference.getClass().getName());
    }

    private static String toJakarta(String qualifiedName) {
        return qualifiedName.replaceFirst("^" + OLD_PREFIX, NEW_PREFIX);
    }
}
