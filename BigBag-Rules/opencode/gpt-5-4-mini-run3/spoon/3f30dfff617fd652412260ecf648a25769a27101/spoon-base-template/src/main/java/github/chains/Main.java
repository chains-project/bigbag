package github.chains;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;

public class Main {
    private static final String OLD_PACKAGE = "javax.interceptor";
    private static final String NEW_PACKAGE = "jakarta.interceptor";

    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: <input-source-dir> <output-source-dir>");
            System.exit(1);
        }

        Path inputDir = new File(args[0]).toPath();
        Path outputDir = new File(args[1]).toPath();

        try {
            Files.createDirectories(outputDir);
            Files.walk(inputDir)
                    .sorted(Comparator.naturalOrder())
                    .forEach(source -> copyOrRewrite(inputDir, outputDir, source));
        } catch (IOException e) {
            throw new RuntimeException("Failed to transform sources", e);
        }
    }

    private static void copyOrRewrite(Path inputDir, Path outputDir, Path source) {
        try {
            Path relative = inputDir.relativize(source);
            Path target = outputDir.resolve(relative);
            if (Files.isDirectory(source)) {
                Files.createDirectories(target);
                return;
            }

            Files.createDirectories(target.getParent());
            if (!source.toString().endsWith(".java") && !source.toString().endsWith(".xml")) {
                Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
                return;
            }

            String content = Files.readString(source, StandardCharsets.UTF_8);
            String updated = content.replace(OLD_PACKAGE, NEW_PACKAGE);
            if (source.toString().endsWith(".java")) {
                updated = dedupeImports(updated);
            }
            Files.writeString(target, updated, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Failed to copy or rewrite " + source, e);
        }
    }

    private static String dedupeImports(String content) {
        String[] lines = content.split("\\R", -1);
        StringBuilder result = new StringBuilder(content.length());
        String previousImport = null;
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (line.startsWith("import jakarta.interceptor.")) {
                if (line.equals(previousImport)) {
                    continue;
                }
                previousImport = line;
            } else {
                previousImport = null;
            }
            result.append(line);
            if (i < lines.length - 1) {
                result.append(System.lineSeparator());
            }
        }
        return result.toString();
    }
}
