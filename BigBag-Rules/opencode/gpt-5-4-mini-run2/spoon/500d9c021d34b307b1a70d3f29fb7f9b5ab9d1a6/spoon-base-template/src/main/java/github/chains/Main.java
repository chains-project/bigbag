package github.chains;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.stream.Stream;
import java.util.regex.Pattern;
import java.util.LinkedHashSet;
import java.util.Set;

public final class Main {

    private static final String OLD_PREFIX = "javax.mvc";
    private static final String NEW_PREFIX = "jakarta.mvc";
    private static final Pattern DUPLICATE_IMPORT = Pattern.compile("(?m)^(\\s*import\\s+jakarta\\.mvc(?:\\.[\\w$]+)*)\\.([\\w$]+)\\.\\2;(\\s*)$");

    private Main() {
    }

    public static void main(String[] args) throws IOException {
        if (args.length < 1 || args.length > 2) {
            throw new IllegalArgumentException("Usage: Main <input-source-dir> [<output-source-dir>]");
        }

        Path inputRoot = Paths.get(args[0]).toAbsolutePath().normalize();
        Path outputRoot = args.length == 2
                ? Paths.get(args[1]).toAbsolutePath().normalize()
                : inputRoot;

        try (Stream<Path> paths = Files.walk(inputRoot)) {
            paths.filter(Files::isRegularFile)
                    .forEach(path -> rewriteFile(inputRoot, outputRoot, path));
        }
    }

    private static void rewriteFile(Path inputRoot, Path outputRoot, Path sourceFile) {
        try {
            Path relativePath = inputRoot.relativize(sourceFile);
            Path targetFile = outputRoot.resolve(relativePath);
            Files.createDirectories(targetFile.getParent());

            if (!sourceFile.toString().endsWith(".java")) {
                Files.copy(sourceFile, targetFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
                return;
            }

            String content = Files.readString(sourceFile, StandardCharsets.UTF_8);
            String rewritten = dedupeLines(DUPLICATE_IMPORT.matcher(content.replace(OLD_PREFIX, NEW_PREFIX)).replaceAll("$1.$2;$3"));
            Files.writeString(targetFile, rewritten, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Failed to rewrite " + sourceFile, e);
        }
    }

    private static String dedupeLines(String content) {
        String[] lines = content.split("\\R", -1);
        StringBuilder out = new StringBuilder(content.length());
        Set<String> seenImports = new LinkedHashSet<>();
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (line.startsWith("import ")) {
                if (!seenImports.add(line)) {
                    continue;
                }
            }
            out.append(line);
            if (i + 1 < lines.length) {
                out.append(System.lineSeparator());
            }
        }
        return out.toString();
    }
}
