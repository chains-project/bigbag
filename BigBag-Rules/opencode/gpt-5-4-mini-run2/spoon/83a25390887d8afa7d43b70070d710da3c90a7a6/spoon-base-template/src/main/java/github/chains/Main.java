package github.chains;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.FileVisitResult;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Main {
    private static final String MIN_OKIO_KOTLIN_VERSION = "1.8.0";
    private static final Pattern OKIO_DEPENDENCY = Pattern.compile(
            "<groupId>com\\.squareup\\.okio</groupId>\\s*<artifactId>okio</artifactId>\\s*<version>3\\.4\\.0</version>",
            Pattern.DOTALL);
    private static final Pattern KOTLIN_VERSION = Pattern.compile(
            "<kotlin\\.version>([^<]+)</kotlin\\.version>");

    public static void main(String[] args) throws Exception {
        Path root = args.length > 0 ? Paths.get(args[0]) : Paths.get(".");
        if (!Files.exists(root)) {
            throw new IllegalArgumentException("Input path does not exist: " + root);
        }

        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (file.getFileName().toString().equals("pom.xml")) {
                    rewritePomIfNeeded(file);
                }
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static void rewritePomIfNeeded(Path pom) throws IOException {
        String original = Files.readString(pom, StandardCharsets.UTF_8);
        if (!OKIO_DEPENDENCY.matcher(original).find()) {
            return;
        }

        Matcher matcher = KOTLIN_VERSION.matcher(original);
        if (!matcher.find()) {
            return;
        }

        String currentVersion = matcher.group(1).trim();
        if (compareVersions(currentVersion, MIN_OKIO_KOTLIN_VERSION) >= 0) {
            return;
        }

        String updated = matcher.replaceFirst("<kotlin.version>" + MIN_OKIO_KOTLIN_VERSION + "</kotlin.version>");
        Files.writeString(pom, updated, StandardCharsets.UTF_8);
    }

    private static int compareVersions(String left, String right) {
        String[] leftParts = left.split("\\.");
        String[] rightParts = right.split("\\.");
        int length = Math.max(leftParts.length, rightParts.length);
        for (int i = 0; i < length; i++) {
            int leftPart = i < leftParts.length ? parseIntPart(leftParts[i]) : 0;
            int rightPart = i < rightParts.length ? parseIntPart(rightParts[i]) : 0;
            if (leftPart != rightPart) {
                return Integer.compare(leftPart, rightPart);
            }
        }
        return 0;
    }

    private static int parseIntPart(String part) {
        String digits = part.replaceAll("[^0-9].*$", "");
        if (digits.isEmpty()) {
            return 0;
        }
        return Integer.parseInt(digits);
    }
}
