package github.chains;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.reference.CtPackageReference;
import spoon.reflect.reference.CtReference;
import spoon.reflect.reference.CtTypeReference;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class Main {
    private static final String OLD_PREFIX = "javax.annotation";
    private static final String NEW_PREFIX = "jakarta.annotation";

    public static void main(String[] args) throws IOException {
        if (args.length < 1 || args.length > 2) {
            throw new IllegalArgumentException("Usage: Main <input-source-dir> [output-source-dir]");
        }

        Path inputDir = Paths.get(args[0]).toAbsolutePath().normalize();
        Path outputDir = args.length == 2
                ? Paths.get(args[1]).toAbsolutePath().normalize()
                : Files.createTempDirectory("spoon-jakarta-annotation-").toAbsolutePath().normalize();

        Launcher launcher = new Launcher();
        for (Path sourceRoot : collectSourceRoots(inputDir)) {
            launcher.addInputResource(sourceRoot.toString());
        }
        launcher.setSourceOutputDirectory(outputDir.toFile());
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);

        launcher.addProcessor(new AbstractProcessor<CtTypeReference<?>>() {
            @Override
            public void process(CtTypeReference<?> reference) {
                rewriteReference(reference);
            }
        });
        launcher.addProcessor(new AbstractProcessor<CtImport>() {
            @Override
            public void process(CtImport ctImport) {
                CtReference reference = ctImport.getReference();
                if (reference instanceof CtTypeReference<?>) {
                    CtTypeReference<?> typeReference = (CtTypeReference<?>) reference;
                    if (isLegacyAnnotationType(typeReference)) {
                        ctImport.delete();
                    }
                }
            }
        });

        launcher.buildModel();
        launcher.process();
        launcher.prettyprint();
    }

    private static List<Path> collectSourceRoots(Path inputDir) throws IOException {
        List<Path> sourceRoots = new ArrayList<>();
        if (isSourceRoot(inputDir)) {
            sourceRoots.add(inputDir);
            return sourceRoots;
        }

        try (var paths = Files.walk(inputDir)) {
            paths.filter(Files::isDirectory)
                    .filter(Main::isSourceRoot)
                    .forEach(sourceRoots::add);
        }
        return sourceRoots;
    }

    private static boolean isSourceRoot(Path path) {
        String normalized = path.toString().replace('\\', '/');
        return normalized.endsWith("/src/main/java") || normalized.endsWith("/src/test/java");
    }

    private static void rewriteReference(CtTypeReference<?> reference) {
        CtPackageReference packageReference = reference.getPackage();
        if (packageReference == null) {
            return;
        }

        String packageName = packageReference.getQualifiedName();
        if (!packageName.equals(OLD_PREFIX) && !packageName.startsWith(OLD_PREFIX + ".")) {
            return;
        }

        String rewrittenPackageName = NEW_PREFIX + packageName.substring(OLD_PREFIX.length());
        reference.setPackage(reference.getFactory().Package().createReference(rewrittenPackageName));
    }

    private static boolean isLegacyAnnotationType(CtTypeReference<?> reference) {
        CtPackageReference packageReference = reference.getPackage();
        if (packageReference == null) {
            return false;
        }
        String packageName = packageReference.getQualifiedName();
        return packageName.equals(OLD_PREFIX) || packageName.startsWith(OLD_PREFIX + ".");
    }
}
