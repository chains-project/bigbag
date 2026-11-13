package github.chains;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Pattern;

public class Main {

    private static final Pattern PATTERN = Pattern.compile(
        "new\\s+((?:org\\.hamcrest\\.core\\.)?(?:StringContains|StringStartsWith))\\s*\\(\\s*(?:true|false)\\s*,",
        Pattern.DOTALL
    );

    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            throw new IllegalArgumentException("Usage: Main <input-source-dir> [output-source-dir]");
        }
        final Path input = Paths.get(args[0]).toAbsolutePath().normalize();
        final Path output = args.length > 1
            ? Paths.get(args[1]).toAbsolutePath().normalize()
            : input.resolveSibling(input.getFileName() + "-spooned");
        Files.createDirectories(output);
        try (var paths = Files.walk(input)) {
            paths.filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                .forEach(source -> transform(input, output, source));
        }
    }

    private static void transform(final Path input, final Path output, final Path source) {
        try {
            final String original = Files.readString(source, StandardCharsets.UTF_8);
            final String transformed = PATTERN.matcher(original).replaceAll("new $1(");
            final Path target = output.resolve(input.relativize(source).toString());
            Files.createDirectories(target.getParent());
            Files.writeString(target, transformed, StandardCharsets.UTF_8);
        } catch (final IOException err) {
            throw new IllegalStateException(String.format("Failed to transform %s", source), err);
        }
    }
}
