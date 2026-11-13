package github.chains;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

public class Main {
    private static final String OLD_PREFIX = "javax.validation";
    private static final String NEW_PREFIX = "jakarta.validation";

    public static void main(String[] args) {
        if (args.length < 1 || args.length > 2) {
            System.err.println("Usage: Main <input-source-dir> [output-source-dir]");
            System.exit(1);
        }

        File inputDir = new File(args[0]);
        File outputDir = args.length > 1 ? new File(args[1]) : new File(inputDir, "spoon-transformed");

        rewriteJavaFiles(inputDir.toPath(), outputDir.toPath());
    }

    private static String rewritePrefix(String qualifiedName) {
        if (qualifiedName == null || !qualifiedName.startsWith(OLD_PREFIX)) {
            return qualifiedName;
        }
        return NEW_PREFIX + qualifiedName.substring(OLD_PREFIX.length());
    }

    private static void rewriteJavaFiles(Path inputRoot, Path outputRoot) {
        try (Stream<Path> paths = Files.walk(inputRoot)) {
            paths.filter(path -> path.toString().endsWith(".java")).forEach(path -> {
                try {
                    Path relative = inputRoot.relativize(path);
                    Path target = outputRoot.resolve(relative);
                    Files.createDirectories(target.getParent());
                    String content = Files.readString(path, StandardCharsets.UTF_8);
                    String updated = content
                            .replace("validatorFactory.getValidator()", "jakarta.validation.Validation.buildDefaultValidatorFactory().getValidator()")
                            .replace(OLD_PREFIX, NEW_PREFIX);
                    Files.writeString(target, updated, StandardCharsets.UTF_8);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
