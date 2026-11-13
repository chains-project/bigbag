package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.CompilationUnit;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Main {
    private static final String OLD_PREFIX_REGEX =
            "com\\.google\\.api\\.services\\.cloudresourcemanager\\.(?!v3\\.)";
    private static final String NEW_PREFIX = "com.google.api.services.cloudresourcemanager.v3.";

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            throw new IllegalArgumentException("Usage: Main <source-root>");
        }

        Path sourceRoot = Paths.get(args[0]);
        ParserConfiguration configuration = new ParserConfiguration();
        configuration.setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_11);
        JavaParser parser = new JavaParser(configuration);

        List<Path> javaFiles;
        try (Stream<Path> paths = Files.walk(sourceRoot)) {
            javaFiles =
                    paths.filter(Main::isJavaSourceFile)
                            .collect(Collectors.toList());
        }

        for (Path file : javaFiles) {
            ParseResult<CompilationUnit> parseResult = parser.parse(file);
            if (!parseResult.getResult().isPresent()) {
                throw new IOException("Failed to parse " + file + ": " + parseResult.getProblems());
            }

            CompilationUnit cu = parseResult.getResult().get();
            String original = cu.toString();
            String rewritten = original.replaceAll(OLD_PREFIX_REGEX, NEW_PREFIX);
            rewritten = rewritten.replace("com.google.api.services.cloudresourcemanager.v3.v3.", NEW_PREFIX);
            rewritten = rewritten.replace("com.google.api.services.cloudresourcemanager.v3.v3", "com.google.api.services.cloudresourcemanager.v3");

            if (!rewritten.equals(original)) {
                validateRewrittenSource(parser, file, rewritten);
                Files.writeString(file, rewritten);
            }
        }
    }

    private static boolean isJavaSourceFile(Path path) {
        String normalized = path.toString().replace('\\', '/');
        if (!normalized.endsWith(".java")) {
            return false;
        }
        return !normalized.contains("/target/")
                && !normalized.contains("/.git/")
                && !normalized.contains("/build/");
    }

    private static void validateRewrittenSource(JavaParser parser, Path file, String rewritten)
            throws IOException {
        ParseResult<CompilationUnit> validation = parser.parse(rewritten);
        if (!validation.getResult().isPresent()) {
            throw new IOException("Rewritten source for " + file + " does not parse: " + validation.getProblems());
        }
    }
}
