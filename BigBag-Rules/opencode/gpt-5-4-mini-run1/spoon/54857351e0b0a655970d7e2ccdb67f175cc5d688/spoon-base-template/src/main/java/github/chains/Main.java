package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class Main {

    private static final String OLD_TYPE = "net.lingala.zip4j.core.ZipFile";

    private static final String NEW_TYPE = "net.lingala.zip4j.ZipFile";

    private Main() {
    }

    public static void main(final String[] args) throws Exception {
        if (args.length < 1 || args.length > 2) {
            throw new IllegalArgumentException("Usage: Main <input source dir> [output dir]");
        }

        final File inputDir = new File(args[0]);
        if (!inputDir.isDirectory()) {
            throw new IllegalArgumentException("Input directory does not exist: " + inputDir);
        }

        final File outputDir = args.length == 2 ? new File(args[1]) : inputDir;

        final Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(false);
        launcher.getEnvironment().setCommentEnabled(true);
        launcher.getEnvironment().setShouldCompile(false);
        launcher.addInputResource(inputDir.getAbsolutePath());
        launcher.buildModel();

        rewriteMatchingFiles(launcher.getModel().getElements(new TypeFilter<>(CtImport.class)), outputDir.toPath(), inputDir.toPath());
        rewriteMatchingFiles(launcher.getModel().getElements(new TypeFilter<>(CtTypeReference.class)), outputDir.toPath(), inputDir.toPath());
    }

    private static void rewriteMatchingFiles(final List<?> elements, final Path outputRoot, final Path inputRoot) throws Exception {
        final Set<Path> touched = new LinkedHashSet<>();

        for (Object element : elements) {
            final spoon.reflect.declaration.CtElement ctElement = (spoon.reflect.declaration.CtElement) element;
            if (ctElement.getPosition() == null || ctElement.getPosition().getFile() == null) {
                continue;
            }
            final Path file = ctElement.getPosition().getFile().toPath().toAbsolutePath().normalize();
            if (touched.add(file)) {
                rewriteFile(file);
            }
        }
    }

    private static void rewriteFile(final Path file) throws Exception {
        final String content = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
        final String updated = content.replace(OLD_TYPE, NEW_TYPE);
        if (!content.equals(updated)) {
            Files.write(file, updated.getBytes(StandardCharsets.UTF_8));
        }
    }
}
