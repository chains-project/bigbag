package github.chains;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class Main {
    private static final Pattern OKIO_DEPENDENCY = Pattern.compile(
            "<groupId>\\s*com\\.squareup\\.okio\\s*</groupId>.*?<artifactId>\\s*okio\\s*</artifactId>.*?<version>\\s*3\\.4\\.0\\s*</version>",
            Pattern.DOTALL);
    private static final Pattern KOTLIN_VERSION = Pattern.compile(
            "(<kotlin\\.version>)([^<]+)(</kotlin\\.version>)");
    private static final Pattern KOTLIN_PLUGIN_VERSION = Pattern.compile(
            "(<artifactId>\\s*kotlin-maven-plugin\\s*</artifactId>.*?<version>)([^<]+)(</version>)",
            Pattern.DOTALL);

    public static void main(String[] args) throws IOException {
        Path root = Paths.get(args.length > 0 ? args[0] : ".").toAbsolutePath().normalize();
        try (Stream<Path> paths = Files.walk(root)) {
            paths.filter(path -> path.getFileName().toString().equals("pom.xml"))
                    .sorted(Comparator.naturalOrder())
                    .forEach(Main::rewritePom);
        }
    }

    private static void rewritePom(Path pom) {
        try {
            String original = Files.readString(pom, StandardCharsets.UTF_8);
            if (!OKIO_DEPENDENCY.matcher(original).find()) {
                return;
            }

            String updated = original;
            updated = replaceVersion(updated, KOTLIN_VERSION, "1.8.0");
            updated = replaceVersion(updated, KOTLIN_PLUGIN_VERSION, "1.8.0");

            if (!updated.equals(original)) {
                Files.writeString(pom, updated, StandardCharsets.UTF_8);
            }
        } catch (IOException ex) {
            throw new RuntimeException("Failed to rewrite " + pom, ex);
        }
    }

    private static String replaceVersion(String text, Pattern pattern, String replacement) {
        Matcher matcher = pattern.matcher(text);
        return matcher.replaceAll("$1" + replacement + "$3");
    }
}
