package github.chains;

import java.nio.file.Path;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.charset.StandardCharsets;
import java.io.IOException;
import java.util.Comparator;
import java.util.stream.Stream;
import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.reference.CtPackageReference;
import spoon.reflect.reference.CtReference;
import spoon.reflect.reference.CtTypeReference;

public class Main {
    private static final String OLD_PREFIX = "javax.mvc";
    private static final String NEW_PREFIX = "jakarta.mvc";

    public static void main(String[] args) {
        if (args.length < 1) {
            throw new IllegalArgumentException("Usage: Main <input-source-dir> [output-dir]");
        }

        Path inputDir = Paths.get(args[0]);
        Path outputDir = args.length > 1
                ? Paths.get(args[1])
                : inputDir.resolveSibling(inputDir.getFileName() + "-spoon-transformed");

        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setCommentEnabled(true);
        launcher.getEnvironment().setIgnoreDuplicateDeclarations(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.addInputResource(inputDir.toString());
        launcher.getEnvironment().setSourceOutputDirectory(outputDir.toFile());

        launcher.addProcessor(new AbstractProcessor<CtReference>() {
            @Override
            public void process(CtReference reference) {
                if (reference instanceof CtTypeReference) {
                    rewriteTypeReference((CtTypeReference<?>) reference);
                } else if (reference instanceof CtPackageReference) {
                    rewritePackageReference((CtPackageReference) reference);
                }
            }


            private void rewriteTypeReference(CtTypeReference<?> reference) {
                String qualifiedName = reference.getPackage() == null
                        ? reference.getSimpleName()
                        : reference.getPackage().getQualifiedName() + "." + reference.getSimpleName();
                if (!isAffected(qualifiedName)) {
                    return;
                }

                String rewritten = Main.rewriteQualifiedName(qualifiedName);
                CtTypeReference<?> replacement = getFactory().Type().createReference(rewritten);
                reference.replace(replacement);
            }

            private void rewritePackageReference(CtPackageReference reference) {
                String qualifiedName = reference.getQualifiedName();
                if (!isAffected(qualifiedName)) {
                    return;
                }

                String rewritten = Main.rewriteQualifiedName(qualifiedName);
                CtPackageReference replacement = getFactory().Package().createReference(rewritten);
                reference.replace(replacement);
            }

        });

        launcher.addProcessor(new AbstractProcessor<CtImport>() {
            @Override
            public void process(CtImport ctImport) {
                CtReference reference = ctImport.getReference();
                if (reference instanceof CtTypeReference) {
                    CtTypeReference<?> typeReference = (CtTypeReference<?>) reference;
                    String qualifiedName = typeReference.getPackage() == null
                            ? typeReference.getSimpleName()
                            : typeReference.getPackage().getQualifiedName() + "." + typeReference.getSimpleName();
                    if (isAffected(qualifiedName)) {
                        ctImport.setReference(getFactory().Type().createReference(
                                rewriteQualifiedName(qualifiedName)));
                    }
                } else if (reference instanceof CtPackageReference) {
                    CtPackageReference packageReference = (CtPackageReference) reference;
                    if (isAffected(packageReference.getQualifiedName())) {
                        ctImport.setReference(getFactory().Package().createReference(
                                rewriteQualifiedName(packageReference.getQualifiedName())));
                    }
                }
            }
        });

        launcher.buildModel();
        launcher.process();
        launcher.prettyprint();

        syncTransformedSources(inputDir, outputDir);
    }

    private static String rewriteQualifiedName(String qualifiedName) {
        return NEW_PREFIX + qualifiedName.substring(OLD_PREFIX.length());
    }

    private static boolean isAffected(String qualifiedName) {
        return qualifiedName != null
                && (qualifiedName.equals(OLD_PREFIX) || qualifiedName.startsWith(OLD_PREFIX + "."));
    }

    private static void syncTransformedSources(Path inputDir, Path outputDir) {
        try {
            if (Files.exists(inputDir)) {
                try (Stream<Path> walk = Files.walk(inputDir)) {
                    walk.filter(path -> path.toString().endsWith(".java"))
                            .sorted(Comparator.reverseOrder())
                            .forEach(path -> {
                                try {
                                    Files.deleteIfExists(path);
                                } catch (IOException e) {
                                    throw new RuntimeException(e);
                                }
                            });
                }
            }

            if (!Files.exists(outputDir)) {
                return;
            }

            try (Stream<Path> walk = Files.walk(outputDir)) {
                walk.filter(path -> path.toString().endsWith(".java"))
                        .forEach(path -> {
                            try {
                                Path relative = outputDir.relativize(path);
                                Path target = inputDir.resolve(relative);
                                Files.createDirectories(target.getParent());
                                String content = Files.readString(path, StandardCharsets.UTF_8)
                                        .replace(OLD_PREFIX, NEW_PREFIX);
                                Files.writeString(target, content, StandardCharsets.UTF_8);
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                        });
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
