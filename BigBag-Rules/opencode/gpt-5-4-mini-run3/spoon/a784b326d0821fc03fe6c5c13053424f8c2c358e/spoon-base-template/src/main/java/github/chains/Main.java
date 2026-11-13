package github.chains;

import spoon.Launcher;
import spoon.MavenLauncher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.declaration.CtCompilationUnit;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.reference.CtReference;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

public class Main {
    private static final String OLD_PREFIX = "javax.validation.";
    private static final String NEW_PREFIX = "jakarta.validation.";

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            throw new IllegalArgumentException("Usage: Main <input-source-dir> <output-source-dir>");
        }

        final Path inputDir = Path.of(args[0]).toAbsolutePath();
        final Path outputDir = Path.of(args[1]).toAbsolutePath();
        Files.createDirectories(outputDir);

        final Path projectRoot = findProjectRoot(inputDir);
        final Launcher launcher = new MavenLauncher(projectRoot.toString(), MavenLauncher.SOURCE_TYPE.APP_SOURCE, true);
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setSourceOutputDirectory(outputDir.toFile());
        launcher.getEnvironment().setAutoImports(false);
        launcher.getEnvironment().setComplianceLevel(17);
        launcher.getEnvironment().setCommentEnabled(false);
        launcher.addProcessor(new ValidationNamespaceMigrationProcessor());
        launcher.addProcessor(new ValidationImportMigrationProcessor());
        launcher.run();
        rewriteLegacyValidationNamespaces(outputDir);
    }

    private static Path findProjectRoot(Path inputDir) {
        Path current = inputDir;
        while (current != null) {
            if (Files.exists(current.resolve("pom.xml"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalArgumentException("No pom.xml found above " + inputDir);
    }

    private static void rewriteLegacyValidationNamespaces(Path outputDir) throws IOException {
        try (Stream<Path> paths = Files.walk(outputDir)) {
            paths.filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java")).forEach(path -> {
                try {
                    final String content = Files.readString(path, StandardCharsets.UTF_8);
                    final String rewritten = content.replace(OLD_PREFIX, NEW_PREFIX);
                    if (!content.equals(rewritten)) {
                        Files.writeString(path, rewritten, StandardCharsets.UTF_8);
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        }
    }

    private static final class ValidationNamespaceMigrationProcessor extends AbstractProcessor<CtTypeReference<?>> {
        @Override
        public void process(CtTypeReference<?> typeReference) {
            final String qualifiedName = typeReference.getQualifiedName();
            if (qualifiedName != null && qualifiedName.startsWith(OLD_PREFIX)) {
                typeReference.replace(typeReference.getFactory().Type().createReference(
                        NEW_PREFIX + qualifiedName.substring(OLD_PREFIX.length())));
            }
        }
    }

    private static final class ValidationImportMigrationProcessor extends AbstractProcessor<CtCompilationUnit> {
        @Override
        public void process(CtCompilationUnit compilationUnit) {
            for (CtImport ctImport : compilationUnit.getImports()) {
                final CtReference reference = ctImport.getReference();
                final String qualifiedName = reference == null ? null : reference.toString();
                if (qualifiedName != null && qualifiedName.startsWith(OLD_PREFIX)) {
                    ctImport.setReference(ctImport.getFactory().Type().createReference(
                            NEW_PREFIX + qualifiedName.substring(OLD_PREFIX.length())));
                }
            }
        }
    }
}
