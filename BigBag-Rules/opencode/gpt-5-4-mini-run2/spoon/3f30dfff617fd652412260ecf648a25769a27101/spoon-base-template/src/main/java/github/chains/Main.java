package github.chains;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Main {
    private static final String OLD_PACKAGE = "javax.interceptor";
    private static final String NEW_PACKAGE = "jakarta.interceptor";

    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            throw new IllegalArgumentException("Expected: <input-source-dir> [output-source-dir]");
        }

        Path inputDir = Paths.get(args[0]).toAbsolutePath().normalize();
        Path outputDir = args.length > 1
                ? Paths.get(args[1]).toAbsolutePath().normalize()
                : inputDir;

        Files.walk(inputDir)
                .filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                .forEach(path -> rewriteFile(inputDir, outputDir, path));
    }

    private static void rewriteFile(Path inputDir, Path outputDir, Path sourceFile) {
        try {
            Path relative = inputDir.relativize(sourceFile);
            Path targetFile = outputDir.resolve(relative);
            Files.createDirectories(targetFile.getParent());
            String content = Files.readString(sourceFile, StandardCharsets.UTF_8);
            Files.writeString(targetFile, content.replace(OLD_PACKAGE, NEW_PACKAGE), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Failed to rewrite " + sourceFile, e);
        }
    }
}
