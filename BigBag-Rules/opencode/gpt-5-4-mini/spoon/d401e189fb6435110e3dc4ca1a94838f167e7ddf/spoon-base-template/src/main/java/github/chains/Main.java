package github.chains;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Main {

    private static final Pattern LOGBACK_VERSION = Pattern.compile(
            "(<groupId>\\s*ch\\.qos\\.logback\\s*</groupId>\\s*<artifactId>\\s*logback-classic\\s*</artifactId>\\s*<version>)([^<]+)(</version>)",
            Pattern.DOTALL);
    private static final Pattern SLF4J_VERSION = Pattern.compile(
            "(<groupId>\\s*org\\.slf4j\\s*</groupId>\\s*<artifactId>\\s*slf4j-api\\s*</artifactId>\\s*<version>)([^<]+)(</version>)",
            Pattern.DOTALL);
    private static final String FIXED_SLF4J_VERSION = "2.0.9";

    public static void main(final String[] args) throws IOException {
        if (args.length < 1) {
            throw new IllegalArgumentException("Expected input project directory");
        }

        final Path root = Paths.get(args[0]);
        Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(final Path file, final BasicFileAttributes attrs) throws IOException {
                if (file.getFileName().toString().equals("pom.xml")) {
                    rewritePom(file);
                }
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static void rewritePom(final Path pom) throws IOException {
        String content = new String(Files.readAllBytes(pom), StandardCharsets.UTF_8);
        if (!LOGBACK_VERSION.matcher(content).find()) {
            return;
        }

        final Matcher slf4j = SLF4J_VERSION.matcher(content);
        if (slf4j.find()) {
            content = slf4j.replaceAll("$1" + FIXED_SLF4J_VERSION + "$3");
        }

        Files.write(pom, content.getBytes(StandardCharsets.UTF_8));
    }
}
