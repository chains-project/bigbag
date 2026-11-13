package github.chains;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Main {
    private static final String JACKSON_GROUP = "com.fasterxml.jackson.core";
    private static final String JACKSON_DATABIND = "jackson-databind";
    private static final String JACKSON_CORE = "jackson-core";
    private static final Pattern CORE_VERSION_PATTERN = Pattern.compile(
            "(<dependency>\\s*<groupId>" + JACKSON_GROUP + "</groupId>\\s*<artifactId>" + JACKSON_CORE + "</artifactId>\\s*<version>)([^<]+)(</version>)",
            Pattern.DOTALL);

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            throw new IllegalArgumentException("Usage: Main <project-root>");
        }

        Path root = Paths.get(args[0]).toAbsolutePath().normalize();
        for (Path pom : findPomFiles(root)) {
            fixPom(pom);
        }
    }

    private static List<Path> findPomFiles(Path root) throws IOException {
        List<Path> result = new ArrayList<>();
        Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (file.getFileName() != null && Objects.equals(file.getFileName().toString(), "pom.xml")) {
                    result.add(file);
                }
                return FileVisitResult.CONTINUE;
            }
        });
        return result;
    }

    private static void fixPom(Path pomFile) throws IOException {
        String content = Files.readString(pomFile);
        String databindVersion = findDependencyVersion(content, JACKSON_GROUP, JACKSON_DATABIND);
        if (databindVersion == null) {
            return;
        }

        String updated = updateDependencyVersion(content, normalizeVersion(databindVersion));
        if (!content.equals(updated)) {
            Files.writeString(pomFile, updated);
        }
    }

    private static String findDependencyVersion(String content, String groupId, String artifactId) {
        Pattern pattern = Pattern.compile(
                "<dependency>\\s*<groupId>" + Pattern.quote(groupId) + "</groupId>\\s*<artifactId>" + Pattern.quote(artifactId) + "</artifactId>\\s*<version>([^<]+)</version>",
                Pattern.DOTALL);
        Matcher matcher = pattern.matcher(content);
        return matcher.find() ? matcher.group(1).trim() : null;
    }

    private static String updateDependencyVersion(String content, String newVersion) {
        Matcher matcher = CORE_VERSION_PATTERN.matcher(content);
        String replacement = "$1" + Matcher.quoteReplacement(newVersion) + "$3";
        return matcher.replaceFirst(replacement);
    }

    private static String normalizeVersion(String version) {
        String[] parts = version.split("\\.");
        if (parts.length >= 3) {
            return parts[0] + "." + parts[1] + "." + parts[2];
        }
        return version;
    }
}
