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
import java.util.stream.Stream;

public class Main {
    private static final String OLD_PACKAGE_PREFIX = "org.mockito.runners";
    private static final String NEW_PACKAGE_PREFIX = "org.mockito.junit";

    public static void main(String[] args) {
        Path sourceRoot = Paths.get(args.length > 0 ? args[0] : ".");

        try (Stream<Path> paths = Files.walk(sourceRoot)) {
            paths.filter(Main::isJavaSource).forEach(Main::rewrite);
        } catch (IOException e) {
            throw new RuntimeException("Failed to walk source tree: " + sourceRoot, e);
        }
    }

    private static boolean isJavaSource(Path path) {
        return Files.isRegularFile(path) && path.toString().endsWith(".java");
    }

    private static void rewrite(Path sourceFile) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(sourceFile);
            LexicalPreservingPrinter.setup(cu);

            boolean changed = rewriteImports(cu) | rewriteQualifiedTypes(cu);

            if (changed) {
                Files.writeString(sourceFile, LexicalPreservingPrinter.print(cu));
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to rewrite " + sourceFile, e);
        }
    }

    private static boolean rewriteImports(CompilationUnit cu) {
        boolean changed = false;

        for (ImportDeclaration importDeclaration : cu.getImports()) {
            String importedName = importDeclaration.getNameAsString();
            if (importedName.startsWith(OLD_PACKAGE_PREFIX)) {
                importDeclaration.setName(importedName.replaceFirst(
                    "^" + java.util.regex.Pattern.quote(OLD_PACKAGE_PREFIX),
                    NEW_PACKAGE_PREFIX));
                changed = true;
            }
        }

        return changed;
    }

    private static boolean rewriteQualifiedTypes(CompilationUnit cu) {
        boolean changed = false;

        for (ClassOrInterfaceType type : cu.findAll(ClassOrInterfaceType.class)) {
            String qualifiedName = type.asString();
            if (qualifiedName.startsWith(OLD_PACKAGE_PREFIX + ".")) {
                type.replace(StaticJavaParser.parseClassOrInterfaceType(
                    qualifiedName.replaceFirst(
                        "^" + java.util.regex.Pattern.quote(OLD_PACKAGE_PREFIX),
                        NEW_PACKAGE_PREFIX)));
                changed = true;
            }
        }

        return changed;
    }
}
