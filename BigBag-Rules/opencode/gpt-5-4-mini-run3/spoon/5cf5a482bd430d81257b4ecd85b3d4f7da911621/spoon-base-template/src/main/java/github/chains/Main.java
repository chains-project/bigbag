package github.chains;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;
import java.io.IOException;
import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.reference.CtPackageReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

public class Main {
    private static final String OLD_PREFIX = "javax.";
    private static final String NEW_PREFIX = "jakarta.";

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            throw new IllegalArgumentException("Usage: Main <sourceDir> [outputDir]");
        }

        Path sourceDir = Paths.get(args[0]).toAbsolutePath().normalize();
        if (!Files.isDirectory(sourceDir)) {
            throw new IllegalArgumentException("Source directory does not exist: " + sourceDir);
        }

        Path outputDir = args.length > 1
                ? Paths.get(args[1]).toAbsolutePath().normalize()
                : sourceDir;

        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(false);
        launcher.getEnvironment().setCommentEnabled(true);
        launcher.getEnvironment().setPreserveLineNumbers(true);
        launcher.getEnvironment().setComplianceLevel(17);
        launcher.getEnvironment().setSourceOutputDirectory(outputDir.toFile());
        launcher.addInputResource(sourceDir.toString());

        launcher.buildModel();
        CtModel model = launcher.getModel();
        for (CtTypeReference<?> reference : model.getElements(new TypeFilter<>(CtTypeReference.class))) {
            rewriteTypeReference(reference);
        }

        for (CtPackageReference reference : model.getElements(new TypeFilter<>(CtPackageReference.class))) {
            rewritePackageReference(reference);
        }

        for (CtImport anImport : model.getElements(new TypeFilter<>(CtImport.class))) {
            rewriteImport(anImport);
        }

        launcher.prettyprint();
        rewriteGeneratedSources(outputDir);
    }

    private static void rewriteTypeReference(CtTypeReference<?> reference) {
        String qualifiedName = reference.getQualifiedName();
        if (qualifiedName == null || !qualifiedName.startsWith(OLD_PREFIX)) {
            return;
        }

        String rewritten = NEW_PREFIX + qualifiedName.substring(OLD_PREFIX.length());
        int lastDot = rewritten.lastIndexOf('.');
        if (lastDot < 0) {
            return;
        }

        CtPackageReference pkg = reference.getFactory().createPackageReference();
        pkg.setSimpleName(rewritten.substring(0, lastDot));
        reference.setPackage(pkg);
        reference.setSimpleName(rewritten.substring(lastDot + 1));
    }

    private static void rewritePackageReference(CtPackageReference reference) {
        String qualifiedName = reference.getQualifiedName();
        if (qualifiedName != null && qualifiedName.startsWith(OLD_PREFIX)) {
            reference.setSimpleName(NEW_PREFIX + qualifiedName.substring(OLD_PREFIX.length()));
        }
    }

    private static void rewriteImport(CtImport anImport) {
        var reference = anImport.getReference();
        if (reference == null) {
            return;
        }

        if (reference instanceof CtTypeReference<?> typeReference) {
            rewriteTypeReference(typeReference);
            anImport.setReference(typeReference);
        } else if (reference instanceof CtPackageReference packageReference) {
            rewritePackageReference(packageReference);
            anImport.setReference(packageReference);
        }
    }

    private static void rewriteGeneratedSources(Path outputDir) throws IOException {
        try (var paths = Files.walk(outputDir)) {
            paths.filter(path -> path.toString().endsWith(".java"))
                    .forEach(path -> {
                        try {
                            String content = Files.readString(path, StandardCharsets.UTF_8);
                            String updated = content.replace("javax.", "jakarta.");
                            if (!updated.equals(content)) {
                                Files.writeString(path, updated, StandardCharsets.UTF_8);
                            }
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    });
        }
    }
}
