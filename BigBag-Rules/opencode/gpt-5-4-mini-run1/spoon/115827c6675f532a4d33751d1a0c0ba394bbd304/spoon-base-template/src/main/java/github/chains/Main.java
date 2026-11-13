package github.chains;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

import spoon.Launcher;

public class Main {

    private static final String OLD_PREFIX = "javax.servlet";
    private static final String NEW_PREFIX = "jakarta.servlet";

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            throw new IllegalArgumentException("Usage: Main <input-source-dir> [output-source-dir]");
        }

        Path inputDir = Paths.get(args[0]).toAbsolutePath().normalize();
        Path outputDir = args.length > 1
                ? Paths.get(args[1]).toAbsolutePath().normalize()
                : inputDir.resolveSibling(inputDir.getFileName() + "-spooned");

        Files.createDirectories(outputDir);

        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.addInputResource(inputDir.toString());
        launcher.buildModel();

        try (Stream<Path> paths = Files.walk(inputDir)) {
            paths.filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                    .forEach(sourcePath -> rewriteFile(inputDir, outputDir, sourcePath));
        }
    }

    private static void rewriteFile(Path inputDir, Path outputDir, Path sourcePath) {
        try {
            String source = Files.readString(sourcePath, StandardCharsets.UTF_8);
            if (!source.contains(OLD_PREFIX)) {
                return;
            }

            String rewritten = source.replace(OLD_PREFIX, NEW_PREFIX);
            Path targetPath = outputDir.resolve(inputDir.relativize(sourcePath));
            Files.createDirectories(targetPath.getParent());
            Files.writeString(targetPath, rewritten, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("Failed to rewrite " + sourcePath, e);
        }
    }
}
