package github.chains;

import spoon.Launcher;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.stream.Stream;

public class Main {
    private static final String OLD_PREFIX = "javax.validation";
    private static final String NEW_PREFIX = "jakarta.validation";

    public static void main(String[] args) {
        if (args.length < 1) {
            throw new IllegalArgumentException("Usage: Main <input-source-dir> [output-dir]");
        }

        final Path inputDir = Paths.get(args[0]);
        final Path outputDir = args.length > 1
                ? Paths.get(args[1])
                : inputDir.resolveSibling(inputDir.getFileName().toString() + "-spooned");

        final Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.addInputResource(inputDir.toString());
        launcher.buildModel();

        try (Stream<Path> paths = Files.walk(inputDir)) {
            paths.filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                    .forEach(source -> rewriteSourceFile(inputDir, outputDir, source));
        } catch (IOException e) {
            throw new RuntimeException("Failed to process sources", e);
        }
    }

    private static void rewriteSourceFile(final Path inputDir, final Path outputDir, final Path source) {
        try {
            final Path relative = inputDir.relativize(source);
            final Path target = outputDir.resolve(relative);
            Files.createDirectories(target.getParent());
            final String content = Files.readString(source, StandardCharsets.UTF_8);
            Files.writeString(target, content.replace(OLD_PREFIX, NEW_PREFIX), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Failed to rewrite " + source, e);
        }
    }
}
