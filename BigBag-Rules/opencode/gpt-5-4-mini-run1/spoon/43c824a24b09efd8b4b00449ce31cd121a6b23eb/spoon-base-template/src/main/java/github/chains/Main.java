package github.chains;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.regex.Pattern;

public class Main {
    private static final String OLD_FQN = "de.gwdg.metadataqa.api.json.JsonBranch";
    private static final String NEW_FQN = "de.gwdg.metadataqa.api.json.DataElement";
    private static final Pattern OLD_FQN_PATTERN = Pattern.compile("\\b" + Pattern.quote(OLD_FQN) + "\\b");
    private static final Pattern OLD_SIMPLE_PATTERN = Pattern.compile("\\bJsonBranch\\b");
    private static final Pattern OLD_METHOD_PATTERN = Pattern.compile("\\bgetJsonPath\\s*\\(");

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            throw new IllegalArgumentException("Usage: <input-source-dir> <output-source-dir>");
        }

        Path inputRoot = Paths.get(args[0]).toAbsolutePath();
        Path outputRoot = Paths.get(args[1]).toAbsolutePath();
        Files.createDirectories(outputRoot);

        Files.walk(inputRoot)
            .forEach(source -> {
                try {
                    Path target = outputRoot.resolve(inputRoot.relativize(source).toString());
                    if (Files.isDirectory(source)) {
                        Files.createDirectories(target);
                    } else if (source.toString().endsWith(".java")) {
                        rewriteJava(source, target);
                    } else {
                        Files.createDirectories(target.getParent());
                        Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
    }

    private static void rewriteJava(Path source, Path target) throws IOException {
        String content = Files.readString(source, StandardCharsets.UTF_8);
        content = OLD_FQN_PATTERN.matcher(content).replaceAll(NEW_FQN);
        content = OLD_SIMPLE_PATTERN.matcher(content).replaceAll("DataElement");
        content = OLD_METHOD_PATTERN.matcher(content).replaceAll("getPath(");
        Files.createDirectories(target.getParent());
        Files.writeString(target, content, StandardCharsets.UTF_8);
    }
}
