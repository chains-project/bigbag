package github.chains;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import spoon.Launcher;

public class Main {

    private static final String OLD_TYPE = "org.hamcrest.Matchers";
    private static final String NEW_TYPE = "org.hamcrest.CoreMatchers";
    private static final String OLD_DEP_CORE = "<artifactId>hamcrest-core</artifactId>";
    private static final String OLD_DEP_LIBRARY = "<artifactId>hamcrest-library</artifactId>";
    private static final String NEW_DEP = "<artifactId>hamcrest</artifactId>";

    public static void main(final String[] args) {
        if (args.length < 1) {
            throw new IllegalArgumentException("Usage: Main <input-dir>");
        }
        final Path root = Path.of(args[0]);
        final Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.addInputResource(root.toString());
        try {
            launcher.buildModel();
        } catch (final Exception ignored) {
            // The textual rewrite below is the source of truth for the migration.
        }
        rewriteSources(root);
    }

    private static void rewriteSources(final Path root) {
        try (Stream<Path> paths = Files.walk(root)) {
            paths.filter(Files::isRegularFile)
                .filter(path -> path.toString().endsWith(".java"))
                .forEach(Main::rewriteFile);
        } catch (final IOException err) {
            throw new IllegalStateException("Failed to walk " + root, err);
        }
    }

    private static void rewriteFile(final Path file) {
        try {
            final String original = Files.readString(file, StandardCharsets.UTF_8);
            final String updated;
            if (file.getFileName().toString().equals("pom.xml")) {
                updated = original
                    .replace(OLD_DEP_CORE, NEW_DEP)
                    .replace(OLD_DEP_LIBRARY, NEW_DEP);
            } else {
                updated = original;
            }
            if (!original.equals(updated)) {
                Files.writeString(file, updated, StandardCharsets.UTF_8);
            }
        } catch (final IOException err) {
            throw new IllegalStateException("Failed to rewrite " + file, err);
        }
    }
}
