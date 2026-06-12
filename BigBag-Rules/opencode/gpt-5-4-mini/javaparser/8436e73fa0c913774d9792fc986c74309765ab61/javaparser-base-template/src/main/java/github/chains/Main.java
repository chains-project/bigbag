package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.expr.Name;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Main {
    private static final String[][] PACKAGE_REWRITES = {
        {"javax.jws", "jakarta.jws"},
        {"javax.xml.ws", "jakarta.xml.ws"}
    };

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            throw new IllegalArgumentException("Expected one argument: source root path");
        }

        Path sourceRoot = Paths.get(args[0]).toAbsolutePath().normalize();
        if (!Files.isDirectory(sourceRoot)) {
            throw new IllegalArgumentException("Source root does not exist or is not a directory: " + sourceRoot);
        }

        try (var paths = Files.walk(sourceRoot)) {
            paths.filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                .forEach(Main::rewriteFile);
        }
    }

    private static void rewriteFile(Path file) {
        try {
            CompilationUnit compilationUnit = StaticJavaParser.parse(file);
            boolean changed = false;

            for (ImportDeclaration importDeclaration : compilationUnit.getImports()) {
                String importName = importDeclaration.getNameAsString();
                String rewritten = rewrite(importName);
                if (rewritten != null) {
                    importDeclaration.setName(StaticJavaParser.parseName(rewritten));
                    changed = true;
                }
            }

            changed |= rewriteNames(compilationUnit.findAll(Name.class));

            if (changed) {
                Files.writeString(file, compilationUnit.toString(), StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to rewrite " + file, e);
        } catch (RuntimeException e) {
            throw new RuntimeException("Failed to parse " + file, e);
        }
    }

    private static boolean rewriteNames(java.util.List<Name> names) {
        boolean changed = false;
        for (Name name : names) {
            String current = name.asString();
            String rewritten = rewrite(current);
            if (rewritten != null) {
                name.replace(StaticJavaParser.parseName(rewritten));
                changed = true;
            }
        }
        return changed;
    }

    private static String rewrite(String name) {
        for (String[] mapping : PACKAGE_REWRITES) {
            String oldPrefix = mapping[0];
            if (name.equals(oldPrefix) || name.startsWith(oldPrefix + ".")) {
                return mapping[1] + name.substring(oldPrefix.length());
            }
        }
        return null;
    }
}
