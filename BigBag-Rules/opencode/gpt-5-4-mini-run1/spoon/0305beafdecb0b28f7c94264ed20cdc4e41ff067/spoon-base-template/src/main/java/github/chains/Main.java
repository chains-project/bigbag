package github.chains;

import spoon.Launcher;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

public class Main {

    private static final String OLD_TIMEOUT_EXCEPTION = "com.mysql.jdbc.exceptions.MySQLTimeoutException";
    private static final String NEW_TIMEOUT_EXCEPTION = "com.mysql.cj.jdbc.exceptions.MySQLTimeoutException";
    private static final String OLD_MYSQL_DRIVER = "com.mysql.jdbc.Driver";
    private static final String NEW_MYSQL_DRIVER = "com.mysql.cj.jdbc.Driver";

    public static void main(String[] args) {
        if (args.length != 1) {
            throw new IllegalArgumentException("Expected exactly one argument: the source directory to transform");
        }

        final Path sourceDir = Paths.get(args[0]).toAbsolutePath().normalize();

        final Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(false);
        launcher.addInputResource(sourceDir.toString());
        launcher.buildModel();

        try (Stream<Path> paths = Files.walk(sourceDir)) {
            paths.filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                    .forEach(Main::rewriteJavaFile);
        } catch (final IOException ex) {
            throw new IllegalStateException("Failed to rewrite source files", ex);
        }
    }

    private static void rewriteJavaFile(final Path file) {
        try {
            final String original = Files.readString(file, StandardCharsets.UTF_8);
            final String updated = original
                    .replace(OLD_TIMEOUT_EXCEPTION, NEW_TIMEOUT_EXCEPTION)
                    .replace(OLD_MYSQL_DRIVER, NEW_MYSQL_DRIVER);
            if (!original.equals(updated)) {
                Files.writeString(file, updated, StandardCharsets.UTF_8);
            }
        } catch (final IOException ex) {
            throw new IllegalStateException("Failed to rewrite " + file, ex);
        }
    }
}
