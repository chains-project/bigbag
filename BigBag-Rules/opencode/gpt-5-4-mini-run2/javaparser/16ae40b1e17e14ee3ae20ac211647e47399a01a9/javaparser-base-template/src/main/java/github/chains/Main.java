package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.stream.Stream;

public class Main {

    private static final String OLD_ZIPFILE = "net.lingala.zip4j.core.ZipFile";
    private static final String NEW_ZIPFILE = "net.lingala.zip4j.ZipFile";

    public static void main(String[] args) {
        if (args.length == 0) {
            throw new IllegalArgumentException("Expected source root path as the first argument");
        }

        final Path sourceRoot = Paths.get(args[0]);
        try {
            transformSourceTree(sourceRoot);
        } catch (IOException e) {
            throw new RuntimeException("Failed to transform sources under " + sourceRoot, e);
        }
    }

    private static void transformSourceTree(final Path sourceRoot) throws IOException {
        try (Stream<Path> paths = Files.walk(sourceRoot)) {
            paths.filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                    .sorted(Comparator.naturalOrder())
                    .forEach(Main::transformFileSafely);
        }
    }

    private static void transformFileSafely(final Path path) {
        try {
            transformFile(path);
        } catch (IOException e) {
            throw new RuntimeException("Failed to transform " + path, e);
        }
    }

    private static void transformFile(final Path path) throws IOException {
        final CompilationUnit compilationUnit = StaticJavaParser.parse(path, StandardCharsets.UTF_8);

        LexicalPreservingPrinter.setup(compilationUnit);
        boolean changed = false;

        for (ImportDeclaration importDeclaration : compilationUnit.findAll(ImportDeclaration.class)) {
            if (importDeclaration.getNameAsString().equals(OLD_ZIPFILE)) {
                importDeclaration.setName(NEW_ZIPFILE);
                changed = true;
            }
        }

        for (ObjectCreationExpr objectCreationExpr : compilationUnit.findAll(ObjectCreationExpr.class)) {
            if (objectCreationExpr.getType().asString().equals(OLD_ZIPFILE)) {
                objectCreationExpr.setType(NEW_ZIPFILE);
                changed = true;
            }
        }

        if (changed) {
            Files.writeString(path, LexicalPreservingPrinter.print(compilationUnit), StandardCharsets.UTF_8);
        }
    }
}
