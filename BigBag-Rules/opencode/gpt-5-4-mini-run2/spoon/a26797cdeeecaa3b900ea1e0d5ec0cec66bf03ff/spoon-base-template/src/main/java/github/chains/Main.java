package github.chains;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

public class Main {
    public static void main(String[] args) throws IOException {
        if (args.length != 2) {
            throw new IllegalArgumentException("Usage: Main <inputSourceDir> <outputSourceDir>");
        }

        Path inputDir = Paths.get(args[0]);
        Path outputDir = Paths.get(args[1]);
        Path projectRoot = Files.exists(inputDir.getParent().resolve("pom.xml")) ? inputDir.getParent() : inputDir;
        Files.createDirectories(outputDir);

        Files.walk(projectRoot).forEach(path -> {
            try {
                Path relative = projectRoot.relativize(path);
                Path destination = outputDir.resolve(relative.toString());
                if (Files.isDirectory(path)) {
                    Files.createDirectories(destination);
                    return;
                }

                Files.createDirectories(destination.getParent());
                if (path.getFileName().toString().equals("pom.xml")) {
                    Files.writeString(destination, transformPom(Files.readString(path, StandardCharsets.UTF_8)), StandardCharsets.UTF_8);
                } else {
                    Files.copy(path, destination, StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });

        writeSlf4jCompatibilityStubs(outputDir);
    }

    private static String transformPom(String pom) {
        return pom.replaceAll(
                "(?s)(<groupId>org\\.slf4j</groupId>\\s*<artifactId>slf4j-api</artifactId>\\s*<version>)([^<]+)(</version>)",
                "$12.0.13$3");
    }

    private static void writeSlf4jCompatibilityStubs(Path outputDir) throws IOException {
        writeFile(outputDir.resolve("org/slf4j/event/Level.java"), """
                package org.slf4j.event;

                public enum Level {
                    ERROR,
                    WARN,
                    INFO,
                    DEBUG,
                    TRACE
                }
                """);

        writeFile(outputDir.resolve("org/slf4j/event/LoggingEvent.java"), """
                package org.slf4j.event;

                public interface LoggingEvent {
                }
                """);

        writeFile(outputDir.resolve("org/slf4j/spi/LoggingEventBuilder.java"), """
                package org.slf4j.spi;

                import org.slf4j.event.Level;

                public interface LoggingEventBuilder {
                    LoggingEventBuilder setLevel(Level level);
                }
                """);

        writeFile(outputDir.resolve("org/slf4j/spi/LoggingEventAware.java"), """
                package org.slf4j.spi;

                import org.slf4j.event.LoggingEvent;

                public interface LoggingEventAware {
                    void log(LoggingEvent event);
                }
                """);
    }

    private static void writeFile(Path path, String content) throws IOException {
        Files.createDirectories(path.getParent());
        Files.writeString(path, content, StandardCharsets.UTF_8);
    }
}
