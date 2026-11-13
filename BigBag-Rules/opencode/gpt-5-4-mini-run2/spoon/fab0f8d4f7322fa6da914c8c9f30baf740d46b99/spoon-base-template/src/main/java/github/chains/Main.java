package github.chains;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

public final class Main {

    private static final Pattern IMPORT_BYTES = Pattern.compile("(?m)^import\\s+org\\.cactoos\\.io\\.BytesOf;\\s*$");

    private static final Pattern IMPORT_HEX = Pattern.compile("(?m)^import\\s+org\\.cactoos\\.text\\.HexOf;\\s*$");

    private static final Pattern OLD_CALL = Pattern.compile(
        "new\\s+HexOf\\s*\\(\\s*new\\s+BytesOf\\s*\\((.*?)\\)\\s*\\)\\s*\\.\\s*asString\\s*\\(\\s*\\)",
        Pattern.DOTALL
    );

    private Main() {
        // utility class
    }

    public static void main(final String[] args) throws IOException {
        if (args.length < 1) {
            throw new IllegalArgumentException("Usage: Main <input-dir> [output-dir]");
        }
        final Path input = Path.of(args[0]);
        final Path output = Path.of(args.length > 1 ? args[1] : args[0]);
        Files.walk(input)
            .forEach(path -> {
                try {
                    final Path target = output.resolve(input.relativize(path).toString());
                    if (Files.isDirectory(path)) {
                        Files.createDirectories(target);
                    } else if (path.toString().endsWith(".java")) {
                        Files.createDirectories(target.getParent());
                        Files.writeString(target, transform(Files.readString(path)), StandardCharsets.UTF_8);
                    } else {
                        Files.createDirectories(target.getParent());
                        Files.copy(path, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    }
                } catch (final IOException err) {
                    throw new IllegalStateException(err);
                }
            });
    }

    private static String transform(final String source) {
        String result = IMPORT_BYTES.matcher(source).replaceAll("");
        result = IMPORT_HEX.matcher(result).replaceAll("");
        result = OLD_CALL.matcher(result).replaceAll("java.util.HexFormat.of().formatHex($1)");
        return result;
    }
}
