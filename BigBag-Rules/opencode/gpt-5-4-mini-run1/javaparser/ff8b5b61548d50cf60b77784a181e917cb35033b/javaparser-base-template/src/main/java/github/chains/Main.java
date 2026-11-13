package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;

public class Main {
    private static final String OLD_RUNNER = "org.mockito.runners.MockitoJUnitRunner";
    private static final String NEW_RUNNER = "org.mockito.junit.MockitoJUnitRunner";

    public static void main(String[] args) {
        if (args.length == 0) {
            throw new IllegalArgumentException("Expected a source root path");
        }

        Path sourceRootPath = Paths.get(args[0]);
        try {
            transformSourceTree(sourceRootPath);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void transformSourceTree(Path sourceRootPath) throws IOException {
        ParserConfiguration configuration = new ParserConfiguration();
        JavaParser parser = new JavaParser(configuration);

        try (var paths = Files.walk(sourceRootPath)) {
            paths.filter(path -> path.toString().endsWith(".java"))
                .forEach(path -> {
                    try {
                        String original = Files.readString(path, StandardCharsets.UTF_8);
                        if (!original.contains(OLD_RUNNER)) {
                            return;
                        }

                        CompilationUnit cu = parser.parse(original).getResult().orElseThrow();
                        if (cu.findAll(com.github.javaparser.ast.expr.Name.class).stream()
                            .noneMatch(name -> name.asString().contains("MockitoJUnitRunner"))) {
                            return;
                        }

                        String updated = original.replace(OLD_RUNNER, NEW_RUNNER);
                        if (!original.equals(updated)) {
                            Files.writeString(path, updated, StandardCharsets.UTF_8);
                        }
                    } catch (Exception e) {
                        throw new RuntimeException("Failed to transform " + path, e);
                    }
                });
        }
    }
}
