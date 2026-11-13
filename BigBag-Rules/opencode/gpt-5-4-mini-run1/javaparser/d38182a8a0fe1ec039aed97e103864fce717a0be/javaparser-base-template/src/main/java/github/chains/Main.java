package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

public class Main {

    private static final String OLD_QUALIFIED = "com.artipie.http.auth.Authentication.User";

    private static final String NEW_QUALIFIED = "com.artipie.http.auth.AuthUser";

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            throw new IllegalArgumentException("Expected one argument: source directory");
        }
        final Path root = Paths.get(args[0]);
        try (Stream<Path> paths = Files.walk(root)) {
            paths.filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                .forEach(Main::transformFileUnchecked);
        }
    }

    private static void transformFileUnchecked(final Path file) {
        try {
            transformFile(file);
        } catch (final IOException err) {
            throw new IllegalStateException(String.format("Failed to transform %s", file), err);
        }
    }

    private static void transformFile(final Path file) throws IOException {
        final String source = Files.readString(file, StandardCharsets.UTF_8);
        final String transformed = transformSource(source);
        if (!transformed.equals(source)) {
            Files.writeString(file, transformed, StandardCharsets.UTF_8);
        }
    }

    private static String transformSource(final String source) {
        String transformed = source;
        transformed = transformed.replace(OLD_QUALIFIED, NEW_QUALIFIED);
        transformed = transformed.replaceAll(
            "(?<![A-Za-z0-9_$])Authentication\\.User(?![A-Za-z0-9_$])",
            "AuthUser"
        );
        transformed = transformed.replaceAll(
            "new\\s+AuthUser\\(([^,\\n\\r()]+)\\)",
            "new AuthUser($1, \"\")"
        );
        final CompilationUnit cu = StaticJavaParser.parse(transformed);
        for (final ObjectCreationExpr expr : cu.findAll(ObjectCreationExpr.class)) {
            if (expr.getType().asString().equals("AuthUser") && expr.getArguments().size() == 1) {
                expr.addArgument("\"\"");
            }
        }
        return cu.toString();
    }
}
