package github.chains;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

public class Main {
    private static final String OLD_PACKAGE_PREFIX = "javax.";
    private static final String NEW_PACKAGE_PREFIX = "jakarta.";

    public static void main(String[] args) {
        if (args.length != 1) {
            throw new IllegalArgumentException("Usage: Main <source-directory>");
        }

        Path sourceRoot = Paths.get(args[0]).toAbsolutePath().normalize();
        if (!Files.isDirectory(sourceRoot)) {
            throw new IllegalArgumentException("Not a directory: " + sourceRoot);
        }

        try (Stream<Path> paths = Files.walk(sourceRoot)) {
            paths.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .forEach(Main::rewriteJavaFileUnchecked);
        } catch (IOException e) {
            throw new RuntimeException("Failed to walk source tree: " + sourceRoot, e);
        }
    }

    private static void rewriteJavaFileUnchecked(Path file) {
        try {
            rewriteJavaFile(file);
        } catch (IOException e) {
            throw new RuntimeException("Failed to rewrite " + file, e);
        }
    }

    private static void rewriteJavaFile(Path file) throws IOException {
        String source = Files.readString(file, StandardCharsets.UTF_8);
        String updated = source.replace(OLD_PACKAGE_PREFIX, NEW_PACKAGE_PREFIX);

        if (!updated.equals(source)) {
            Files.writeString(file, updated, StandardCharsets.UTF_8);
        }
    }
}
