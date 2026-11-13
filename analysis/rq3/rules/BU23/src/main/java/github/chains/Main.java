package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;

public class Main {
    private static final Map<String, String> PACKAGE_RENAMES = new LinkedHashMap<>();

    static {
        PACKAGE_RENAMES.put("org.apache.struts2.dispatcher.ng.filter", "org.apache.struts2.dispatcher.filter");
    }

    public static void main(String[] args) {
        if (args.length < 1 || args.length > 2) {
            throw new IllegalArgumentException("Usage: Main <sourceDir> [targetDir]");
        }

        Path sourceDir = Paths.get(args[0]);
        Path targetDir = args.length == 2 ? Paths.get(args[1]) : sourceDir;

        try (Stream<Path> paths = Files.walk(sourceDir)) {
            paths.filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                    .forEach(path -> rewriteFile(sourceDir, targetDir, path));
        } catch (IOException e) {
            throw new RuntimeException("Failed to traverse source tree", e);
        }
    }

    private static void rewriteFile(Path sourceDir, Path targetDir, Path sourceFile) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(sourceFile);
            boolean changed = rewriteImports(cu) | rewriteQualifiedTypes(cu);

            Path targetFile = targetDir.resolve(sourceDir.relativize(sourceFile));
            if (changed || !Files.exists(targetFile)) {
                Files.createDirectories(targetFile.getParent());
                Files.writeString(targetFile, cu.toString(), StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to rewrite " + sourceFile, e);
        }
    }

    private static boolean rewriteImports(CompilationUnit cu) {
        boolean changed = false;
        for (ImportDeclaration importDeclaration : cu.getImports()) {
            String name = importDeclaration.getNameAsString();
            for (Map.Entry<String, String> rename : PACKAGE_RENAMES.entrySet()) {
                String oldPrefix = rename.getKey();
                if (name.startsWith(oldPrefix)) {
                    importDeclaration.setName(name.replace(oldPrefix, rename.getValue()));
                    changed = true;
                }
            }
        }
        return changed;
    }

    private static boolean rewriteQualifiedTypes(CompilationUnit cu) {
        boolean changed = false;
        for (ClassOrInterfaceType type : cu.findAll(ClassOrInterfaceType.class)) {
            String typeText = type.asString();
            for (Map.Entry<String, String> rename : PACKAGE_RENAMES.entrySet()) {
                String oldPrefix = rename.getKey();
                if (typeText.startsWith(oldPrefix + ".") || typeText.equals(oldPrefix)) {
                    type.replace(StaticJavaParser.parseClassOrInterfaceType(typeText.replace(oldPrefix, rename.getValue())));
                    changed = true;
                }
            }
        }
        return changed;
    }
}
