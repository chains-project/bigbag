package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.expr.Name;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

public class Main {
    private static final String OLD_PREFIX = "javax.validation";
    private static final String NEW_PREFIX = "jakarta.validation";

    public static void main(String[] args) {
        if (args.length != 1) {
            throw new IllegalArgumentException("Usage: Main <source-root>");
        }

        final Path root = Paths.get(args[0]);
        try (Stream<Path> paths = Files.walk(root)) {
            paths.filter(path -> path.toString().endsWith(".java"))
                    .forEach(Main::rewriteFile);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void rewriteFile(final Path file) {
        try {
            final CompilationUnit cu = StaticJavaParser.parse(file);
            LexicalPreservingPrinter.setup(cu);

            boolean changed = false;

            for (final ImportDeclaration importDeclaration : cu.getImports()) {
                if (importDeclaration.getNameAsString().startsWith(OLD_PREFIX)) {
                    importDeclaration.setName(importDeclaration.getNameAsString().replace(OLD_PREFIX, NEW_PREFIX));
                    changed = true;
                }
            }

            changed |= rewriteNames(cu.findAll(Name.class));

            if (changed) {
                Files.writeString(file, LexicalPreservingPrinter.print(cu));
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to rewrite " + file, e);
        }
    }

    private static boolean rewriteNames(final java.util.List<Name> names) {
        boolean changed = false;
        for (final Name name : names) {
            if (name.asString().startsWith(OLD_PREFIX)) {
                name.replace(StaticJavaParser.parseName(name.asString().replace(OLD_PREFIX, NEW_PREFIX)));
                changed = true;
            }
        }
        return changed;
    }
}
