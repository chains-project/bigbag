package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.Name;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

public class Main {
    private static final String OLD_PREFIX = "org.assertj.core.internal.bytebuddy";
    private static final String NEW_PREFIX = "net.bytebuddy";

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            throw new IllegalArgumentException("Expected one argument: source directory");
        }

        Path sourceDir = Paths.get(args[0]);
        try (Stream<Path> paths = Files.walk(sourceDir)) {
            paths.filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                    .forEach(Main::rewriteFile);
        }
    }

    private static void rewriteFile(Path path) {
        try {
            CompilationUnit compilationUnit = StaticJavaParser.parse(path);
            boolean changed = false;

            for (ImportDeclaration importDeclaration : compilationUnit.getImports()) {
                String importName = importDeclaration.getNameAsString();
                if (importName.startsWith(OLD_PREFIX)) {
                    importDeclaration.setName(new Name(importName.replace(OLD_PREFIX, NEW_PREFIX)));
                    changed = true;
                }
            }

            if (changed) {
                Files.write(path, compilationUnit.toString().getBytes(StandardCharsets.UTF_8));
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to rewrite imports in " + path, e);
        }
    }
}
