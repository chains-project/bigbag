package github.chains;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Main {
    private static final Pattern FLYWAY_BLOCK = Pattern.compile(
            "(?s)(?<indent>^[ \\t]*)(?<final>final\\s+)?(?<type>(?:org\\.flywaydb\\.core\\.)?Flyway)\\s+(?<var>[A-Za-z_][A-Za-z0-9_]*)\\s*=\\s*new\\s+(?:org\\.flywaydb\\.core\\.)?Flyway\\s*\\(\\s*\\)\\s*;\\s*"
                    + "(?:(?:\\1)\\k<var>\\.setDataSource\\((?<ds>[^;]+)\\)\\s*;\\s*)?"
                    + "(?:(?:\\1)\\k<var>\\.setClassLoader\\((?<cl>[^;]+)\\)\\s*;\\s*)?"
                    + "(?:(?:\\1)\\k<var>\\.setLocations\\((?<loc>[^;]+)\\)\\s*;\\s*)?"
                    + "(?:(?:\\1)\\k<var>\\.setValidateOnMigrate\\((?<val>[^;]+)\\)\\s*;\\s*)?"
                    + "(?:^[ \\t]*return\\s+\\k<var>\\s*;)",
            Pattern.MULTILINE);

    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            throw new IllegalArgumentException("Usage: Main <input-path> [output-path]");
        }

        final Path input = Paths.get(args[0]).toAbsolutePath().normalize();
        final Path output = args.length > 1
                ? Paths.get(args[1]).toAbsolutePath().normalize()
                : input.resolveSibling(input.getFileName() + "-spoon-out");

        if (Files.isDirectory(input) && Files.isRegularFile(input.resolve("pom.xml"))) {
            rewriteProject(input, output);
        } else {
            rewritePath(input, output);
        }
    }

    private static void rewriteProject(Path projectRoot, Path outputRoot) throws IOException {
        final List<Path> sources = new ArrayList<>();
        collectJavaSources(projectRoot.resolve("src/main/java"), sources);
        collectJavaSources(projectRoot.resolve("src/test/java"), sources);
        if (sources.isEmpty()) {
            collectJavaSources(projectRoot, sources);
        }
        for (Path source : sources) {
            final Path relative = projectRoot.relativize(source);
            final Path target = outputRoot.resolve(relative);
            Files.createDirectories(target.getParent());
            Files.writeString(target, transform(Files.readString(source), source), StandardCharsets.UTF_8);
        }
    }

    private static void rewritePath(Path input, Path output) throws IOException {
        if (Files.isDirectory(input)) {
            final List<Path> sources = new ArrayList<>();
            collectJavaSources(input, sources);
            for (Path source : sources) {
                final Path relative = input.relativize(source);
                final Path target = output.resolve(relative);
                Files.createDirectories(target.getParent());
                Files.writeString(target, transform(Files.readString(source), source), StandardCharsets.UTF_8);
            }
        } else {
            Files.createDirectories(output.getParent());
            Files.writeString(output, transform(Files.readString(input), input), StandardCharsets.UTF_8);
        }
    }

    private static void collectJavaSources(Path root, List<Path> sources) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        try (var stream = Files.walk(root)) {
            stream.filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java")).forEach(sources::add);
        }
    }

    private static String transform(String source, Path file) {
        final Matcher matcher = FLYWAY_BLOCK.matcher(source);
        final StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            final String indent = matcher.group("indent");
            final String finalModifier = matcher.group("final") == null ? "" : matcher.group("final");
            final String type = matcher.group("type");
            final String variable = matcher.group("var");
            final String dataSource = matcher.group("ds");
            final String classLoader = matcher.group("cl");
            final String locations = matcher.group("loc");
            final String validate = matcher.group("val");

            final StringBuilder builder = new StringBuilder();
            builder.append(indent)
                    .append(finalModifier)
                    .append(type)
                    .append(' ')
                    .append(variable)
                    .append(" = ")
                    .append(type)
                    .append(".configure(");
            if (classLoader != null) {
                builder.append(classLoader.trim());
            }
            builder.append(")");
            if (dataSource != null) {
                builder.append(".dataSource(").append(dataSource.trim()).append(")");
            }
            if (locations != null) {
                builder.append(".locations(").append(locations.trim()).append(")");
            }
            if (validate != null) {
                builder.append(".validateOnMigrate(").append(validate.trim()).append(")");
            }
            builder.append(".load();");
            matcher.appendReplacement(result, Matcher.quoteReplacement(builder.toString()));
        }
        matcher.appendTail(result);
        return result.toString();
    }
}
