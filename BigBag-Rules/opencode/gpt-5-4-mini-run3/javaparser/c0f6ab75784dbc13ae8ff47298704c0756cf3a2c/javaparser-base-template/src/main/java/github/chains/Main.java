package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

public class Main {
    private static final String TARGET_METHOD = "addEnabledLanguages";

    public static void main(String[] args) throws IOException {
        Path sourceRoot = Paths.get(args.length > 0 ? args[0] : ".");
        try (Stream<Path> paths = Files.walk(sourceRoot)) {
            paths.filter(Main::isJavaSource)
                    .forEach(
                            path -> {
                                try {
                                    transform(path);
                                } catch (IOException e) {
                                    throw new RuntimeException("Failed to transform " + path, e);
                                }
                            });
        }
    }

    private static boolean isJavaSource(Path path) {
        String normalized = path.toString();
        return normalized.endsWith(".java") && !normalized.contains("/target/") && !normalized.contains("\\target\\");
    }

    private static void transform(Path path) throws IOException {
        String original = Files.readString(path, StandardCharsets.UTF_8);
        CompilationUnit compilationUnit = StaticJavaParser.parse(original);
        LexicalPreservingPrinter.setup(compilationUnit);

        boolean[] changed = {false};
        compilationUnit.findAll(MethodCallExpr.class).forEach(call -> changed[0] |= rewrite(call));

        if (changed[0]) {
            Files.writeString(path, LexicalPreservingPrinter.print(compilationUnit), StandardCharsets.UTF_8);
        }
    }

    private static boolean rewrite(MethodCallExpr call) {
        if (!TARGET_METHOD.equals(call.getNameAsString()) || call.getScope().isEmpty()) {
            return false;
        }

        call.replace(call.getScope().get().clone());
        return true;
    }
}
