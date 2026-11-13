package github.chains;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Pattern;

public class Main {
    private static final Pattern IMPORT_PATTERN = Pattern.compile("(?m)^import\\s+com\\.gargoylesoftware\\.htmlunit\\.ScriptResult;\\R");
    private static final Pattern NEW_PATTERN = Pattern.compile("new\\s+ScriptResult\\(([^)]+)\\)\\.getJavaScriptResult\\(\\)");
    private static final Pattern NEW_ONLY_PATTERN = Pattern.compile("new\\s+ScriptResult\\(([^)]+)\\)");

    public static void main(String[] args) {
        Path root = Paths.get(args.length > 0 ? args[0] : ".").toAbsolutePath().normalize();
        try {
            Files.walk(root)
                    .filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                    .forEach(Main::transform);
        }
        catch (IOException exception) {
            throw new RuntimeException(exception);
        }
    }

    private static void transform(Path file) {
        try {
            String source = Files.readString(file, StandardCharsets.UTF_8);
            String updated = IMPORT_PATTERN.matcher(source).replaceAll("");
            updated = NEW_PATTERN.matcher(updated).replaceAll("$1");
            updated = NEW_ONLY_PATTERN.matcher(updated).replaceAll("$1");

            if (!updated.equals(source)) {
                Files.writeString(file, updated, StandardCharsets.UTF_8);
            }
        }
        catch (IOException exception) {
            throw new RuntimeException("Failed to transform " + file, exception);
        }
    }
}
