package github.chains;

import spoon.Launcher;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public class Main {
    private static final String OLD_MYSQL_TIMEOUT_EXCEPTION = "com.mysql.jdbc.exceptions.MySQLTimeoutException";
    private static final String NEW_MYSQL_TIMEOUT_EXCEPTION = "com.mysql.cj.jdbc.exceptions.MySQLTimeoutException";

    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: Main <input-source-dir> <output-dir>");
            System.exit(1);
        }

        Path inputDir = Paths.get(args[0]);
        Path outputDir = Paths.get(args[1]);

        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.addInputResource(inputDir.toString());
        launcher.setSourceOutputDirectory(outputDir.toFile());
        launcher.buildModel();

        launcher.prettyprint();
        rewriteOutput(outputDir);
    }

    private static void rewriteOutput(Path outputDir) {
        try (Stream<Path> paths = Files.walk(outputDir)) {
            paths.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .forEach(path -> {
                        try {
                            String content = Files.readString(path, StandardCharsets.UTF_8);
                            String updated = content.replace(OLD_MYSQL_TIMEOUT_EXCEPTION, NEW_MYSQL_TIMEOUT_EXCEPTION);
                            updated = deduplicateConsecutiveLines(updated);
                            if (!content.equals(updated)) {
                                Files.writeString(path, updated, StandardCharsets.UTF_8);
                            }
                        } catch (IOException e) {
                            throw new RuntimeException("Failed to rewrite " + path, e);
                        }
                    });
        } catch (IOException e) {
            throw new RuntimeException("Failed to walk output directory", e);
        }
    }

    private static String deduplicateConsecutiveLines(String content) {
        String[] lines = content.split("\\R", -1);
        List<String> compacted = new ArrayList<>();
        String previous = null;
        for (String line : lines) {
            if (!line.equals(previous)) {
                compacted.add(line);
                previous = line;
            }
        }
        return String.join(System.lineSeparator(), compacted);
    }
}
