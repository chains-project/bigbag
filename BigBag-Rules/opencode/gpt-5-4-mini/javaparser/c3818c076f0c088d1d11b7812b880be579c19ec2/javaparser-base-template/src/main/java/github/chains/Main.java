package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.expr.Name;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

public class Main {
    private static final String OLD_PACKAGE = "javax.validation";
    private static final String NEW_PACKAGE = "jakarta.validation";

    public static void main(String[] args) {
        final Path sourceRoot = args.length > 0 ? Paths.get(args[0]) : Paths.get(".");

        try (Stream<Path> paths = Files.walk(sourceRoot)) {
            paths.filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                    .forEach(Main::rewriteFile);
        } catch (IOException e) {
            throw new RuntimeException("Failed to transform sources under " + sourceRoot, e);
        }
    }

    private static void rewriteFile(final Path path) {
        try {
            final CompilationUnit cu = StaticJavaParser.parse(path);
            LexicalPreservingPrinter.setup(cu);

            final boolean changed = rewriteImports(cu) | rewriteQualifiedNames(cu);
            if (changed) {
                Files.writeString(path, LexicalPreservingPrinter.print(cu), StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to transform file " + path, e);
        }
    }

    private static boolean rewriteImports(final CompilationUnit cu) {
        boolean changed = false;
        for (final ImportDeclaration importDeclaration : cu.getImports()) {
            final String name = importDeclaration.getNameAsString();
            if (name.equals(OLD_PACKAGE) || name.startsWith(OLD_PACKAGE + ".")) {
                importDeclaration.setName(rewriteName(name));
                changed = true;
            }
        }
        return changed;
    }

    private static boolean rewriteQualifiedNames(final CompilationUnit cu) {
        boolean changed = false;
        for (final Name name : cu.findAll(Name.class)) {
            if (name.getParentNode().filter(parent -> parent instanceof ImportDeclaration).isPresent()) {
                continue;
            }

            final String qualifiedName = name.asString();
            if (qualifiedName.equals(OLD_PACKAGE) || qualifiedName.startsWith(OLD_PACKAGE + ".")) {
                name.replace(rewriteName(qualifiedName));
                changed = true;
            }
        }
        return changed;
    }

    private static Name rewriteName(final String qualifiedName) {
        return StaticJavaParser.parseName(qualifiedName.replace(OLD_PACKAGE, NEW_PACKAGE));
    }
}
