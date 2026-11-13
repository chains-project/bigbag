package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;

public class Main {
    private static final String OLD_PREFIX = "com.google.api.services.cloudresourcemanager";
    private static final String NEW_PREFIX = "com.google.api.services.cloudresourcemanager.v3";

    public static void main(String[] args) {
        Path sourceRoot = args.length > 0 ? Paths.get(args[0]) : Paths.get(".");

        try {
            List<Path> javaFiles = Files.walk(sourceRoot)
                    .filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                    .sorted(Comparator.naturalOrder())
                    .toList();

            for (Path javaFile : javaFiles) {
                transform(javaFile);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to transform sources under " + sourceRoot, e);
        }
    }

    private static void transform(Path javaFile) throws IOException {
        String original = Files.readString(javaFile);
        String transformed = original
                .replace(NEW_PREFIX + ".v3", NEW_PREFIX)
                .replace(OLD_PREFIX + ".model", NEW_PREFIX + ".model")
                .replace(OLD_PREFIX, NEW_PREFIX);

        if (!original.equals(transformed)) {
            CompilationUnit cu = StaticJavaParser.parse(transformed);
            Files.writeString(javaFile, cu.toString());
        }
    }
}
