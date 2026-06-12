package github.chains;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class Main {
    public static void main(String[] args) throws IOException {
        if (args.length != 1 && args.length != 2) {
            throw new IllegalArgumentException("Usage: Main <input-source-dir> [output-source-dir]");
        }

        Path inputDir = Paths.get(args[0]).toAbsolutePath().normalize();
        Path outputDir = args.length == 2 ? Paths.get(args[1]).toAbsolutePath().normalize() : inputDir;

        try (Stream<Path> paths = Files.walk(inputDir)) {
            paths.filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                    .forEach(path -> {
                        Path relative = inputDir.relativize(path);
                        Path target = outputDir.resolve(relative);
                        try {
                            String source = Files.readString(path, StandardCharsets.UTF_8);
                            String rewritten = rewriteFlywayBlock(source);
                            if (!source.equals(rewritten)) {
                                Files.createDirectories(target.getParent());
                                Files.writeString(target, rewritten, StandardCharsets.UTF_8);
                            } else if (!path.equals(target)) {
                                Files.createDirectories(target.getParent());
                                Files.writeString(target, source, StandardCharsets.UTF_8);
                            }
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    });
        }
    }

    private static String rewriteFlywayBlock(String source) {
        Pattern pattern = Pattern.compile(
                "(?s)([\t ]*)final\\s+(?:org\\.flywaydb\\.core\\.)?Flyway\\s+(\\w+)\\s*=\\s*new\\s+(?:org\\.flywaydb\\.core\\.)?Flyway\\s*\\(\\s*\\)\\s*;\\s*"
                        + "\\1\\2\\.setDataSource\\((.*?)\\)\\s*;\\s*"
                        + "\\1\\2\\.setClassLoader\\((.*?)\\)\\s*;\\s*"
                        + "\\1\\2\\.setLocations\\((.*?)\\)\\s*;\\s*"
                        + "\\1\\2\\.setValidateOnMigrate\\((.*?)\\)\\s*;\\s*"
                        + "\\1return\\s+\\2\\s*;",
                Pattern.MULTILINE);

        Matcher matcher = pattern.matcher(source);
        StringBuffer rewritten = new StringBuffer();
        while (matcher.find()) {
            String indent = matcher.group(1);
            String dataSource = matcher.group(3).trim();
            String classLoader = matcher.group(4).trim();
            String locations = matcher.group(5).trim();
            String validate = matcher.group(6).trim();
            String replacement = indent + "return org.flywaydb.core.Flyway.configure(" + classLoader + ")\n"
                    + indent + "\t\t.dataSource(" + dataSource + ")\n"
                    + indent + "\t\t.locations(" + locations + ")\n"
                    + indent + "\t\t.validateOnMigrate(" + validate + ")\n"
                    + indent + "\t\t.load();";
            matcher.appendReplacement(rewritten, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(rewritten);
        return rewritten.toString();
    }
}
