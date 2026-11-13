package github.chains;

import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public class Main {
    private static final String OLD_PREFIX = "javax.";
    private static final String NEW_PREFIX = "jakarta.";

    public static void main(String[] args) {
        if (args.length < 1 || args.length > 2) {
            throw new IllegalArgumentException("Usage: Main <input-source-dir> [output-source-dir]");
        }

        Path projectRoot = Paths.get(args[0]).toAbsolutePath().normalize();
        Path outputRoot = args.length == 2
                ? Paths.get(args[1]).toAbsolutePath().normalize()
                : projectRoot;

        try {
            if (!projectRoot.equals(outputRoot)) {
                copyTree(projectRoot, outputRoot);
            }

            List<Path> sourceRoots = collectSourceRoots(outputRoot);
            for (Path sourceRoot : sourceRoots) {
                rewriteJavaFiles(sourceRoot);
            }
        } catch (Exception exception) {
            throw new RuntimeException("Failed to rewrite MVC namespace references", exception);
        }
    }

    private static List<Path> collectSourceRoots(Path projectRoot) {
        List<Path> roots = new ArrayList<>();
        Path mainJava = projectRoot.resolve("src/main/java");
        Path testJava = projectRoot.resolve("src/test/java");
        if (Files.isDirectory(mainJava)) {
            roots.add(mainJava);
        }
        if (Files.isDirectory(testJava)) {
            roots.add(testJava);
        }
        if (roots.isEmpty()) {
            roots.add(projectRoot);
        }
        return roots;
    }

    private static void copyTree(Path sourceRoot, Path targetRoot) throws Exception {
        try (Stream<Path> paths = Files.walk(sourceRoot)) {
            paths.filter(Files::isRegularFile)
                    .forEach(source -> {
                        Path relative = sourceRoot.relativize(source);
                        Path target = targetRoot.resolve(relative);
                        try {
                            Files.createDirectories(target.getParent());
                            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
                        } catch (Exception exception) {
                            throw new RuntimeException(exception);
                        }
                    });
        }
    }

    private static void rewriteJavaFiles(Path sourceRoot) throws Exception {
        try (Stream<Path> paths = Files.walk(sourceRoot)) {
            paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".java"))
                    .forEach(path -> {
                        try {
                            String content = Files.readString(path);
                            if (!content.contains(OLD_PREFIX)) {
                                return;
                            }

                            String rewritten = content.replace(OLD_PREFIX, NEW_PREFIX);
                            Files.writeString(path, rewritten);
                        } catch (Exception exception) {
                            throw new RuntimeException(exception);
                        }
                    });
        }
    }

    private static String rewritePackageName(String originalPackage) {
        if (originalPackage.equals(OLD_PREFIX)) {
            return NEW_PREFIX;
        }
        if (originalPackage.startsWith(OLD_PREFIX + ".")) {
            return NEW_PREFIX + originalPackage.substring(OLD_PREFIX.length());
        }
        return originalPackage;
    }
}
