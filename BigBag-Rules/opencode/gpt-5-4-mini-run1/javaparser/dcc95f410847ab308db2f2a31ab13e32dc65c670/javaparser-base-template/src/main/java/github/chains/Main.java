package github.chains;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Main {
    private static final Pattern SLF4J_API_VERSION = Pattern.compile(
            "(<groupId>org\\.slf4j</groupId>\\s*<artifactId>slf4j-api</artifactId>\\s*<version>)(1\\.[^<]+)(</version>)",
            Pattern.DOTALL);
    private static final Pattern LOGBACK_CLASSIC_VERSION = Pattern.compile(
            "(<groupId>ch\\.qos\\.logback</groupId>\\s*<artifactId>logback-classic</artifactId>\\s*<version>)([^<]+)(</version>)",
            Pattern.DOTALL);
    private static final Pattern LOGBACK_CORE_DEPENDENCY = Pattern.compile(
            "<dependency>\\s*<groupId>ch\\.qos\\.logback</groupId>\\s*<artifactId>logback-core</artifactId>\\s*<version>[^<]+</version>\\s*</dependency>",
            Pattern.DOTALL);

    public static void main(String[] args) {
        if (args.length == 0) {
            throw new IllegalArgumentException("Expected a Maven project root path");
        }

        Path projectRoot = Paths.get(args[0]).toAbsolutePath().normalize();
        Path pom = projectRoot.resolve("pom.xml");
        if (!Files.exists(pom)) {
            throw new IllegalArgumentException("No pom.xml found at " + pom);
        }

        try {
            rewritePomIfNeeded(pom);
        } catch (IOException e) {
            throw new RuntimeException("Failed to update " + pom, e);
        }
    }

    private static void rewritePomIfNeeded(Path pom) throws IOException {
        String pomText = Files.readString(pom, StandardCharsets.UTF_8);
        if (!isLogback14Project(pomText)) {
            return;
        }

        Matcher logbackClassicMatcher = LOGBACK_CLASSIC_VERSION.matcher(pomText);
        if (!logbackClassicMatcher.find()) {
            return;
        }

        String logbackVersion = logbackClassicMatcher.group(2).trim();
        String updated = SLF4J_API_VERSION.matcher(pomText).replaceFirst("$12.0.7$3");
        updated = ensureLogbackCoreDependency(updated, logbackVersion);
        Files.writeString(pom, updated, StandardCharsets.UTF_8);
    }

    private static String ensureLogbackCoreDependency(String pomText, String version) {
        String dependency = "        <dependency>\n"
                + "            <groupId>ch.qos.logback</groupId>\n"
                + "            <artifactId>logback-core</artifactId>\n"
                + "            <version>" + version + "</version>\n"
                + "        </dependency>\n";

        if (LOGBACK_CORE_DEPENDENCY.matcher(pomText).find()) {
            return LOGBACK_CORE_DEPENDENCY.matcher(pomText).replaceAll(Matcher.quoteReplacement(dependency.trim()));
        }

        int logbackClassicEnd = pomText.indexOf("</dependency>", pomText.indexOf("<artifactId>logback-classic</artifactId>"));
        if (logbackClassicEnd < 0) {
            return pomText;
        }

        int insertPos = pomText.indexOf("\n", logbackClassicEnd);
        if (insertPos < 0) {
            insertPos = logbackClassicEnd + "</dependency>".length();
        } else {
            insertPos += 1;
        }

        return pomText.substring(0, insertPos) + dependency + pomText.substring(insertPos);
    }

    private static boolean isLogback14Project(String pomText) {
        return pomText.contains("<groupId>ch.qos.logback</groupId>")
                && pomText.contains("<artifactId>logback-classic</artifactId>")
                && pomText.matches("(?s).*<version>1\\.4\\.[0-9]+</version>.*");
    }
}
