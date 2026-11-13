package github.chains;

import spoon.Launcher;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

public class Main {
    private static final String OLD_PACKAGE = "develop.p2p.lib";
    private static final String NEW_PACKAGE = "tokyo.peya.lib";

    public static void main(String[] args) {
        if (args.length == 0) {
            throw new IllegalArgumentException("Usage: Main <input-source-dir> [output-source-dir]");
        }

        String inputDir = args[0];
        String outputDir = args.length > 1 ? args[1] : inputDir + "-rewritten";

        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(false);
        launcher.getEnvironment().setCommentEnabled(true);
        launcher.getEnvironment().setSourceOutputDirectory(new File(outputDir));
        launcher.addInputResource(inputDir);

        launcher.run();
        rewriteGeneratedSources(Paths.get(outputDir));
    }

    private static void rewriteGeneratedSources(Path outputRoot) {
        try (Stream<Path> paths = Files.walk(outputRoot)) {
            paths.filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                .forEach(Main::rewriteFile);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to rewrite generated sources", e);
        }
    }

    private static void rewriteFile(Path file) {
        try {
            String content = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
            String rewritten = content.replace(OLD_PACKAGE, NEW_PACKAGE);
            if (!content.equals(rewritten)) {
                Files.write(file, rewritten.getBytes(StandardCharsets.UTF_8));
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to rewrite " + file, e);
        }
    }

    private static boolean isOldApiType(String qualifiedName) {
        return qualifiedName != null && (qualifiedName.equals(OLD_PACKAGE) || qualifiedName.startsWith(OLD_PACKAGE + "."));
    }

    private static String rewriteQualifiedName(String qualifiedName) {
        if (qualifiedName == null || !qualifiedName.startsWith(OLD_PACKAGE)) {
            return qualifiedName;
        }

        return NEW_PACKAGE + qualifiedName.substring(OLD_PACKAGE.length());
    }
}
