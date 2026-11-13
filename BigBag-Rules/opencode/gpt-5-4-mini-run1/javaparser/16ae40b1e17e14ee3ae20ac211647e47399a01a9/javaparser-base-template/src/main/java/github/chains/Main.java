package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;

public class Main {

    private static final String OLD_ZIPFILE_IMPORT = "net.lingala.zip4j.core.ZipFile";

    private static final String NEW_ZIPFILE_IMPORT = "net.lingala.zip4j.ZipFile";

    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Usage: Main <source-root>");
            System.exit(1);
        }

        final Path sourceRoot = Paths.get(args[0]);
        try {
            rewriteSourceTree(sourceRoot);
        } catch (IOException e) {
            System.err.println("Failed to rewrite sources: " + e.getMessage());
            System.exit(1);
        }
    }

    private static void rewriteSourceTree(Path sourceRoot) throws IOException {
        try (var paths = Files.walk(sourceRoot)) {
            List<Path> javaFiles = paths
                    .filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                    .sorted(Comparator.naturalOrder())
                    .toList();

            for (Path file : javaFiles) {
                rewriteFile(file);
            }
        }
    }

    private static void rewriteFile(Path file) throws IOException {
        CompilationUnit compilationUnit = StaticJavaParser.parse(file, StandardCharsets.UTF_8);
        LexicalPreservingPrinter.setup(compilationUnit);

        boolean changed = false;

        for (ImportDeclaration importDeclaration : compilationUnit.getImports()) {
            if (importDeclaration.getNameAsString().equals(OLD_ZIPFILE_IMPORT)) {
                importDeclaration.setName(NEW_ZIPFILE_IMPORT);
                changed = true;
            }
        }

        for (ClassOrInterfaceType type : compilationUnit.findAll(ClassOrInterfaceType.class)) {
            if (type.asString().equals(OLD_ZIPFILE_IMPORT)
                    || (type.getNameAsString().equals("ZipFile")
                    && type.getScope().isPresent()
                    && type.getScope().get().asString().equals("net.lingala.zip4j.core"))) {
                type.replace(StaticJavaParser.parseType(NEW_ZIPFILE_IMPORT).asClassOrInterfaceType());
                changed = true;
            }
        }

        if (changed) {
            Files.writeString(file, LexicalPreservingPrinter.print(compilationUnit), StandardCharsets.UTF_8);
        }
    }
}
