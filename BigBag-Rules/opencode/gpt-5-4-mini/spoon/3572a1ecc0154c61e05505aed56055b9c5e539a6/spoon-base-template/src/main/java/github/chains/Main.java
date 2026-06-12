package github.chains;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.FileVisitResult;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Main {
    private static final String OLD_TYPE = "org.apache.thrift.transport.TFastFramedTransport";
    private static final String NEW_TYPE = "org.apache.thrift.transport.layered.TFastFramedTransport";
    private static final String[] THROWING_TYPES = {
            "org.apache.thrift.TSerializer",
            "org.apache.thrift.TDeserializer",
            "org.apache.thrift.transport.TIOStreamTransport",
            NEW_TYPE
    };

    private Main() {
    }

    public static void main(String[] args) {
        if (args.length < 1) {
            throw new IllegalArgumentException("Usage: Main <sourceDir> [outputDir]");
        }

        Path sourceDir = Paths.get(args[0]).toAbsolutePath().normalize();
        Path outputDir = args.length > 1
                ? Paths.get(args[1]).toAbsolutePath().normalize()
                : sourceDir.resolveSibling(sourceDir.getFileName().toString() + "-transformed");

        try {
            Files.createDirectories(outputDir);
            Files.walkFileTree(sourceDir, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                    Path target = outputDir.resolve(sourceDir.relativize(dir).toString());
                    Files.createDirectories(target);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Path target = outputDir.resolve(sourceDir.relativize(file).toString());
                    if (file.toString().endsWith(".java")) {
                        String updated = transform(Files.readString(file, StandardCharsets.UTF_8));
                        Files.writeString(target, updated, StandardCharsets.UTF_8);
                    } else {
                        Files.copy(file, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static String transform(String source) {
        String updated = source.replace(OLD_TYPE, NEW_TYPE);
        updated = wrapThrowingConstructors(updated);
        return updated;
    }

    private static String wrapThrowingConstructors(String source) {
        String updated = source;
        for (String type : THROWING_TYPES) {
            String simpleName = type.substring(type.lastIndexOf('.') + 1);
            Pattern pattern = Pattern.compile("(?s)(^|\\n)([ \\t]*)([^\\n;]*?(?:return\\s+|[\\w$<>\\[\\].]+\\s*=\\s*)?new\\s+(?:" + Pattern.quote(type) + "|" + Pattern.quote(simpleName) + ")\\s*\\(.*?\\);)");
            Matcher matcher = pattern.matcher(updated);
            StringBuffer sb = new StringBuffer();
            while (matcher.find()) {
                String prefix = matcher.group(1);
                String indent = matcher.group(2);
                String statement = matcher.group(3).trim();
                String wrapped = prefix + indent + "try { " + statement + " } catch (org.apache.thrift.transport.TTransportException e) { throw new RuntimeException(e); }";
                matcher.appendReplacement(sb, Matcher.quoteReplacement(wrapped));
            }
            matcher.appendTail(sb);
            updated = sb.toString();
        }
        return updated;
    }
}
