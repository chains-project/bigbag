package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Stream;

public class Main {
    private static final String OLD_ENV = "io.dropwizard.setup.Environment";
    private static final String NEW_ENV = "io.dropwizard.core.setup.Environment";
    private static final String DROPLESSWIZARD_CLIENT = "dropwizard-client";
    private static final String DROPLESSWIZARD_CORE = "dropwizard-core";
    private static final String DROPLESSWIZARD_TESTING = "dropwizard-testing";
    private static final String TARGET_VERSION = "4.0.0";

    public static void main(String[] args) {
        if (args.length == 0) {
            throw new IllegalArgumentException("Expected a source directory path");
        }

        Path sourceRoot = Paths.get(args[0]);
        try (Stream<Path> paths = Files.walk(sourceRoot)) {
            paths.forEach(Main::transformPath);
        } catch (IOException e) {
            throw new RuntimeException("Failed to walk source tree: " + sourceRoot, e);
        }
    }

    private static void transformPath(Path path) {
        String fileName = path.getFileName().toString();
        if (fileName.endsWith(".java")) {
            transformFile(path);
        } else if (fileName.equals("pom.xml")) {
            transformPom(path);
        }
    }

    private static void transformFile(Path path) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(path);
            LexicalPreservingPrinter.setup(cu);

            boolean changed = replaceImports(cu) | replaceQualifiedTypes(cu);

            if (changed) {
                Files.writeString(path, LexicalPreservingPrinter.print(cu));
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to transform " + path, e);
        }
    }

    private static void transformPom(Path path) {
        try {
            String content = Files.readString(path);
            String updated = content;
            if (updated.contains("<artifactId>" + DROPLESSWIZARD_CLIENT + "</artifactId>")) {
                updated = replaceDependencyVersion(updated, DROPLESSWIZARD_CORE, TARGET_VERSION);
                updated = replaceDependencyVersion(updated, DROPLESSWIZARD_TESTING, TARGET_VERSION);
            }
            if (!updated.equals(content)) {
                Files.writeString(path, updated);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to transform " + path, e);
        }
    }

    private static String replaceDependencyVersion(String content, String artifactId, String version) {
        String marker = "<artifactId>" + artifactId + "</artifactId>";
        int artifactIndex = content.indexOf(marker);
        if (artifactIndex < 0) {
            return content;
        }

        int versionStart = content.indexOf("<version>", artifactIndex);
        int versionEnd = versionStart >= 0 ? content.indexOf("</version>", versionStart) : -1;
        if (versionStart < 0 || versionEnd < 0) {
            return content;
        }

        return content.substring(0, versionStart)
                + "<version>" + version + "</version>"
                + content.substring(versionEnd + "</version>".length());
    }

    private static boolean replaceImports(CompilationUnit cu) {
        boolean changed = false;
        for (ImportDeclaration importDeclaration : cu.getImports()) {
            if (importDeclaration.getNameAsString().equals(OLD_ENV)) {
                importDeclaration.setName(StaticJavaParser.parseName(NEW_ENV));
                changed = true;
            }
        }
        return changed;
    }

    private static boolean replaceQualifiedTypes(CompilationUnit cu) {
        boolean changed = false;
        List<ClassOrInterfaceType> types = cu.findAll(ClassOrInterfaceType.class);
        for (ClassOrInterfaceType type : types) {
            if (OLD_ENV.equals(type.asString())) {
                type.replace(StaticJavaParser.parseClassOrInterfaceType(NEW_ENV));
                changed = true;
            }
        }
        return changed;
    }
}
