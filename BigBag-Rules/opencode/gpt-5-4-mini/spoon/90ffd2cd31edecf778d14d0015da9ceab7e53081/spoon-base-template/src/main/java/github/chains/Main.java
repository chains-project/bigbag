package github.chains;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Main {

    private static final Pattern LOGBACK_CLASSIC = Pattern.compile(
            "<artifactId>logback-classic</artifactId>\\s*<version>([^<]+)</version>",
            Pattern.DOTALL);
    private static final Pattern SLF4J_API = Pattern.compile(
            "(<artifactId>slf4j-api</artifactId>\\s*<version>)([^<]+)(</version>)",
            Pattern.DOTALL);

    public static void main(String[] args) throws IOException {
        Path root = args.length > 0 ? Paths.get(args[0]) : Paths.get(".");
        root = root.toAbsolutePath().normalize();

        Files.walk(root)
                .filter(path -> path.getFileName().toString().equals("pom.xml"))
                .forEach(Main::rewritePom);
    }

    private static void rewritePom(Path pom) {
        try {
            String original = Files.readString(pom);
            Matcher logbackMatcher = LOGBACK_CLASSIC.matcher(original);
            if (!logbackMatcher.find()) {
                return;
            }

            String logbackVersion = logbackMatcher.group(1).trim();
            if (!logbackVersion.startsWith("1.4.")) {
                return;
            }

            Matcher slf4jMatcher = SLF4J_API.matcher(original);
            if (!slf4jMatcher.find()) {
                return;
            }

            String current = slf4jMatcher.group(2).trim();
            if (isAtLeast20(current)) {
                return;
            }

            String updated = SLF4J_API.matcher(original).replaceAll("$1" + "2.0.0" + "$3");
            if (!updated.equals(original)) {
                Files.writeString(pom, updated, StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to update " + pom, e);
        }
    }

    private static boolean isAtLeast20(String version) {
        return version != null && version.trim().startsWith("2.");
    }
}
