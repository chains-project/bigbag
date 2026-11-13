package github.chains;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.reference.CtPackageReference;
import spoon.reflect.reference.CtTypeReference;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class Main {
    private static final String OLD_PREFIX = "javax.validation";
    private static final String NEW_PREFIX = "jakarta.validation";

    public static void main(String[] args) {
        if (args.length < 1) {
            throw new IllegalArgumentException("Usage: Main <input-source-dir> [output-dir]");
        }

        final String inputDir = args[0];
        final String outputDir = args.length > 1 ? args[1] : inputDir + "-transformed";

        final Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(false);
        launcher.addInputResource(inputDir);
        launcher.setSourceOutputDirectory(outputDir);
        launcher.addProcessor(new ValidationPrefixRewriteProcessor());
        launcher.run();
        rewriteGeneratedSources(Path.of(outputDir));
    }

    private static void rewriteGeneratedSources(Path outputDir) {
        try (var paths = Files.walk(outputDir)) {
            paths.filter(path -> path.toString().endsWith(".java")).forEach(Main::rewriteSourceFile);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void rewriteSourceFile(Path path) {
        try {
            final String content = Files.readString(path, StandardCharsets.UTF_8);
            final String updated = content.replace(OLD_PREFIX, NEW_PREFIX);
            if (!content.equals(updated)) {
                Files.writeString(path, updated, StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static final class ValidationPrefixRewriteProcessor extends AbstractProcessor<CtElement> {
        @Override
        public void process(CtElement element) {
            if (element instanceof CtImport ctImport) {
                rewriteImport(ctImport);
            } else if (element instanceof CtTypeReference<?> typeReference) {
                rewriteTypeReference(typeReference);
            }
        }

        private void rewriteImport(CtImport ctImport) {
            final Object reference = ctImport.getReference();
            if (reference instanceof CtTypeReference<?> typeReference) {
                final String qualifiedName = typeReference.getQualifiedName();
                if (isOldApi(qualifiedName)) {
                    ctImport.setReference(typeReference.getFactory().Type().createReference(rewriteQualifiedName(qualifiedName)));
                }
            } else if (reference instanceof CtPackageReference packageReference) {
                final String qualifiedName = packageReference.getQualifiedName();
                if (isOldApi(qualifiedName)) {
                    packageReference.setSimpleName(rewriteQualifiedName(qualifiedName));
                }
            }
        }

        private void rewriteTypeReference(CtTypeReference<?> typeReference) {
            final String qualifiedName = typeReference.getQualifiedName();
            if (!isOldApi(qualifiedName)) {
                return;
            }

            final String rewritten = rewriteQualifiedName(qualifiedName);
            final String packageName = rewritten.substring(0, rewritten.lastIndexOf('.'));
            typeReference.setPackage(typeReference.getFactory().Package().createReference(packageName));
        }

        private boolean isOldApi(String qualifiedName) {
            return qualifiedName != null && qualifiedName.startsWith(OLD_PREFIX);
        }

        private String rewriteQualifiedName(String qualifiedName) {
            return NEW_PREFIX + qualifiedName.substring(OLD_PREFIX.length());
        }
    }
}
