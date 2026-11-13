package github.chains;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class Main {
    private static final Pattern LOGBACK_CLASSIC_PATTERN = Pattern.compile(
            "(?s)(<artifactId>logback-classic</artifactId>\\s*<version>)([^<]+)(</version>)");
    private static final Pattern SLF4J_API_PATTERN = Pattern.compile(
            "(?s)(<artifactId>slf4j-api</artifactId>\\s*<version>)([^<]+)(</version>)");

    public static void main(String[] args) {
        if (args.length != 1) {
            throw new IllegalArgumentException("Expected one argument: source root");
        }

        Path sourceRoot = Paths.get(args[0]);
        try (Stream<Path> files = Files.walk(sourceRoot)) {
            files.filter(path -> path.getFileName() != null && path.getFileName().toString().equals("pom.xml"))
                    .forEach(Main::transformPom);
        } catch (IOException e) {
            throw new RuntimeException("Failed to walk source tree " + sourceRoot, e);
        }
    }

    private static void transformPom(Path pomFile) {
        try {
            String pom = Files.readString(pomFile);
            Matcher logbackMatcher = LOGBACK_CLASSIC_PATTERN.matcher(pom);
            Matcher slf4jMatcher = SLF4J_API_PATTERN.matcher(pom);

            if (!logbackMatcher.find() || !slf4jMatcher.find()) {
                return;
            }

            String logbackVersion = logbackMatcher.group(2).trim();
            String slf4jVersion = slf4jMatcher.group(2).trim();

            if (logbackVersion.startsWith("1.4.") && slf4jVersion.startsWith("1.7.")) {
                String updated = slf4jMatcher.replaceFirst("$1" + "2.0.9" + "$3");
                Files.writeString(pomFile, updated);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to transform " + pomFile, e);
        }
    }
}
