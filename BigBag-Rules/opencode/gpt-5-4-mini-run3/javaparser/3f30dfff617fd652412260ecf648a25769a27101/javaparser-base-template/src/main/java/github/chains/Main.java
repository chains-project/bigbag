package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

public class Main {
    private static final String OLD_PACKAGE = "javax.interceptor";
    private static final String NEW_PACKAGE = "jakarta.interceptor";
    private static final String OLD_PRIORITY = "javax.annotation.Priority";
    private static final String NEW_PRIORITY = "jakarta.annotation.Priority";

    public static void main(String[] args) {
        Path sourceRoot = Paths.get(args.length > 0 ? args[0] : ".");
        try (Stream<Path> paths = Files.walk(sourceRoot)) {
            paths.filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                    .forEach(Main::transformFile);
        } catch (IOException e) {
            throw new RuntimeException("Failed to walk source tree: " + sourceRoot, e);
        }
    }

    private static void transformFile(Path file) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(file);
            boolean changed = false;
            for (ImportDeclaration importDeclaration : cu.getImports()) {
                String importName = importDeclaration.getNameAsString();
                if (importName.equals(OLD_PACKAGE) || importName.startsWith(OLD_PACKAGE + ".")) {
                    importDeclaration.setName(StaticJavaParser.parseName(importName.replaceFirst(OLD_PACKAGE, NEW_PACKAGE)));
                    changed = true;
                } else if (importName.equals(OLD_PRIORITY)) {
                    importDeclaration.setName(StaticJavaParser.parseName(NEW_PRIORITY));
                    changed = true;
                }
            }

            if (changed) {
                Files.write(file, cu.toString().getBytes(StandardCharsets.UTF_8));
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to transform file: " + file, e);
        }
    }
}
