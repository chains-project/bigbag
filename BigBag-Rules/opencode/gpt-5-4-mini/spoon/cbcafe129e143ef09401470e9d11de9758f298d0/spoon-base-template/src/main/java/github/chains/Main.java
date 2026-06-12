package github.chains;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Main {
    private static final String TARGET_GROUP_ID = "org.slf4j";
    private static final String TARGET_ARTIFACT_ID = "slf4j-api";
    private static final String FIXED_VERSION = "2.0.7";

    private static final Pattern LOGBACK_CLASSIC_VERSION = Pattern.compile(
            "(<artifactId>logback-classic</artifactId>\\s*<version>)([^<]+)(</version>)",
            Pattern.DOTALL);
    private static final Pattern SLF4J_API_BLOCK = Pattern.compile(
            "(<dependency>\\s*<groupId>" + Pattern.quote(TARGET_GROUP_ID) + "</groupId>\\s*<artifactId>" + Pattern.quote(TARGET_ARTIFACT_ID) + "</artifactId>)(.*?)(</dependency>)",
            Pattern.DOTALL);
    private static final Pattern DEPENDENCY_VERSION = Pattern.compile("(<version>)([^<]+)(</version>)", Pattern.DOTALL);

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            throw new IllegalArgumentException("Expected source directory path as the first argument");
        }

        Path sourceDir = Paths.get(args[0]).toAbsolutePath().normalize();
        Path root = args.length > 1
                ? Paths.get(args[1]).toAbsolutePath().normalize()
                : sourceDir;

        Files.walk(sourceDir)
                .filter(path -> path.getFileName().toString().equals("pom.xml"))
                .forEach(pom -> {
                    try {
                        rewritePomIfNeeded(pom, root);
                    } catch (IOException e) {
                        throw new IllegalStateException("Failed to update " + pom, e);
                    }
                });
    }

    private static void rewritePomIfNeeded(Path pom, Path root) throws IOException {
        String content = Files.readString(pom, StandardCharsets.UTF_8);
        Matcher logbackMatcher = LOGBACK_CLASSIC_VERSION.matcher(content);
        if (!logbackMatcher.find()) {
            return;
        }

        String logbackVersion = logbackMatcher.group(2).trim();
        if (!logbackVersion.startsWith("1.4.")) {
            return;
        }

        String updatedContent = content;
        updatedContent = updateDependencyVersion(updatedContent, TARGET_GROUP_ID, TARGET_ARTIFACT_ID, FIXED_VERSION);
        updatedContent = updateDependencyVersion(updatedContent, "ch.qos.logback", "logback-core", logbackVersion);
        updatedContent = ensureDependencyPresent(updatedContent, "ch.qos.logback", "logback-core", logbackVersion);
        Files.writeString(pom, updatedContent, StandardCharsets.UTF_8);
    }

    private static String updateDependencyVersion(String content, String groupId, String artifactId, String version) {
        Pattern blockPattern = Pattern.compile(
                "(<dependency>\\s*<groupId>" + Pattern.quote(groupId) + "</groupId>\\s*<artifactId>" + Pattern.quote(artifactId) + "</artifactId>)(.*?)(</dependency>)",
                Pattern.DOTALL);
        Matcher matcher = blockPattern.matcher(content);
        if (!matcher.find()) {
            return content;
        }

        String block = matcher.group(2);
        Matcher versionMatcher = DEPENDENCY_VERSION.matcher(block);
        if (!versionMatcher.find()) {
            return content;
        }

        if (version.equals(versionMatcher.group(2).trim())) {
            return content;
        }

        String updatedBlock = versionMatcher.replaceFirst("$1" + version + "$3");
        return matcher.replaceFirst(Matcher.quoteReplacement(matcher.group(1) + updatedBlock + matcher.group(3)));
    }

    private static String ensureDependencyPresent(String content, String groupId, String artifactId, String version) {
        Pattern blockPattern = Pattern.compile(
                "<dependency>\\s*<groupId>" + Pattern.quote(groupId) + "</groupId>\\s*<artifactId>" + Pattern.quote(artifactId) + "</artifactId>",
                Pattern.DOTALL);
        if (blockPattern.matcher(content).find()) {
            return content;
        }

        Pattern markerPattern = Pattern.compile("(<artifactId>logback-classic</artifactId>\\s*<version>[^<]+</version>\\s*</dependency>)", Pattern.DOTALL);
        Matcher matcher = markerPattern.matcher(content);
        String dependency = String.join(System.lineSeparator(),
                "        <dependency>",
                "            <groupId>" + groupId + "</groupId>",
                "            <artifactId>" + artifactId + "</artifactId>",
                "            <version>" + version + "</version>",
                "        </dependency>");

        if (matcher.find()) {
            return matcher.replaceFirst(Matcher.quoteReplacement(matcher.group(1) + System.lineSeparator() + dependency));
        }

        return content.replaceFirst("(?s)(</dependencies>)", dependency + System.lineSeparator() + "    $1");
    }
}
