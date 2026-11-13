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
import java.util.Optional;
import java.util.stream.Stream;

public class Main {

    private static final String OLD_TYPE = "com.github.javaparser.printer.PrettyPrinterConfiguration";
    private static final String NEW_TYPE = "com.github.javaparser.printer.configuration.PrettyPrinterConfiguration";

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            throw new IllegalArgumentException("Expected one argument: the source directory to rewrite");
        }

        Path sourceRoot = Paths.get(args[0]);
        if (!Files.isDirectory(sourceRoot)) {
            throw new IllegalArgumentException("Not a directory: " + sourceRoot);
        }

        try (Stream<Path> files = Files.walk(sourceRoot)) {
            files.filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                    .forEach(Main::rewriteFile);
        }
    }

    private static void rewriteFile(Path file) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(file);
            LexicalPreservingPrinter.setup(cu);

            boolean changed = rewriteImports(cu) | rewriteTypeReferences(cu);
            if (changed) {
                Files.writeString(file, LexicalPreservingPrinter.print(cu), StandardCharsets.UTF_8);
            }
        } catch (Exception ignored) {
            // Skip files that cannot be parsed; the rule is intended to be applied broadly.
        }
    }

    private static boolean rewriteImports(CompilationUnit cu) {
        boolean changed = false;
        for (ImportDeclaration importDeclaration : cu.findAll(ImportDeclaration.class)) {
            if (importDeclaration.getNameAsString().equals(OLD_TYPE)) {
                importDeclaration.setName(NEW_TYPE);
                changed = true;
            }
        }
        return changed;
    }

    private static boolean rewriteTypeReferences(CompilationUnit cu) {
        boolean changed = false;
        for (ClassOrInterfaceType type : cu.findAll(ClassOrInterfaceType.class)) {
            if (OLD_TYPE.equals(qualifiedName(type))) {
                type.replace(StaticJavaParser.parseClassOrInterfaceType(NEW_TYPE));
                changed = true;
            }
        }
        return changed;
    }

    private static String qualifiedName(ClassOrInterfaceType type) {
        StringBuilder builder = new StringBuilder(type.getNameAsString());
        Optional<ClassOrInterfaceType> scope = type.getScope();
        while (scope.isPresent()) {
            builder.insert(0, scope.get().getNameAsString() + ".");
            scope = scope.get().getScope();
        }
        return builder.toString();
    }
}
