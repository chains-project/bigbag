package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

public final class Main {

    private static final String OLD_PACKAGE_PREFIX = "com.mysql.jdbc.exceptions.";
    private static final String NEW_PACKAGE_PREFIX = "com.mysql.cj.jdbc.exceptions.";

    private Main() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException("Usage: Main <source-root>");
        }

        final Path sourceRoot = Paths.get(args[0]);
        final List<Path> javaFiles = Files.walk(sourceRoot)
                .filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                .collect(Collectors.toList());

        for (Path file : javaFiles) {
            rewriteFile(file);
        }
    }

    private static void rewriteFile(Path file) throws IOException {
        final CompilationUnit cu = StaticJavaParser.parse(file, StandardCharsets.UTF_8);
        LexicalPreservingPrinter.setup(cu);

        boolean changed = false;
        for (ImportDeclaration importDeclaration : cu.getImports()) {
            final String importedName = importDeclaration.getNameAsString();
            if (importedName.startsWith(OLD_PACKAGE_PREFIX)) {
                importDeclaration.setName(importedName.replace(OLD_PACKAGE_PREFIX, NEW_PACKAGE_PREFIX));
                changed = true;
            }
        }

        for (ClassOrInterfaceType type : cu.findAll(ClassOrInterfaceType.class)) {
            final String typeName = type.asString();
            if (typeName.startsWith(OLD_PACKAGE_PREFIX)) {
                final Type replacement = StaticJavaParser.parseType(typeName.replace(OLD_PACKAGE_PREFIX, NEW_PACKAGE_PREFIX));
                type.replace(replacement);
                changed = true;
            }
        }

        if (changed) {
            Files.write(file, LexicalPreservingPrinter.print(cu).getBytes(StandardCharsets.UTF_8));
        }
    }
}
