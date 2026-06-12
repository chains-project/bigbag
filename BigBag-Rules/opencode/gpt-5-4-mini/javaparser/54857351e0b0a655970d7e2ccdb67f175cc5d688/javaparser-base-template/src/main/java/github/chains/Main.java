package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;
import java.util.stream.Stream;

public class Main {

    private static final String OLD_PACKAGE = "net.lingala.zip4j.core";

    private static final String OLD_FQCN = OLD_PACKAGE + ".ZipFile";

    private static final String NEW_PACKAGE = "net.lingala.zip4j";

    private static final String NEW_FQCN = NEW_PACKAGE + ".ZipFile";

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            throw new IllegalArgumentException("Usage: Main <source-directory>");
        }

        final Path sourceRoot = Paths.get(args[0]).toAbsolutePath().normalize();
        if (!Files.isDirectory(sourceRoot)) {
            throw new IllegalArgumentException("Source directory does not exist: " + sourceRoot);
        }

        try (Stream<Path> paths = Files.walk(sourceRoot)) {
            paths.filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                    .forEach(Main::transform);
        }
    }

    private static void transform(final Path file) {
        try {
            final CompilationUnit cu = StaticJavaParser.parse(file, StandardCharsets.UTF_8);
            LexicalPreservingPrinter.setup(cu);

            boolean changed = replaceImports(cu);
            changed |= replaceQualifiedTypeReferences(cu);

            if (changed) {
                Files.writeString(file, LexicalPreservingPrinter.print(cu), StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to transform " + file, e);
        }
    }

    private static boolean replaceImports(final CompilationUnit cu) {
        boolean changed = false;
        for (ImportDeclaration importDeclaration : cu.getImports()) {
            final String name = importDeclaration.getNameAsString();
            if (OLD_FQCN.equals(name)) {
                importDeclaration.setName(NEW_FQCN);
                changed = true;
            } else if (OLD_PACKAGE.equals(name) && importDeclaration.isAsterisk()) {
                importDeclaration.setName(NEW_PACKAGE);
                changed = true;
            }
        }
        return changed;
    }

    private static boolean replaceQualifiedTypeReferences(final CompilationUnit cu) {
        final boolean[] changed = {false};
        cu.findAll(ClassOrInterfaceType.class).forEach(type -> {
            if (matchesQualifiedName(type, OLD_FQCN)) {
                type.replace(StaticJavaParser.parseClassOrInterfaceType(NEW_FQCN));
                changed[0] = true;
            }
        });
        cu.findAll(ObjectCreationExpr.class).forEach(expr -> {
            if (matchesQualifiedName(expr.getType(), OLD_FQCN)) {
                expr.setType(StaticJavaParser.parseClassOrInterfaceType(NEW_FQCN));
                changed[0] = true;
            }
        });
        return changed[0];
    }

    private static boolean matchesQualifiedName(final ClassOrInterfaceType type, final String name) {
        final String qualifiedName = type.getScope()
                .map(scope -> scope.toString() + "." + type.getNameAsString())
                .orElse(type.getNameAsString());
        return Objects.equals(qualifiedName, name);
    }
}
