package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;
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
        if (args.length == 0) {
            throw new IllegalArgumentException("expected source root path");
        }

        final Path sourceRoot = Paths.get(args[0]);
        try (Stream<Path> paths = Files.walk(sourceRoot)) {
            paths.filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                    .forEach(Main::rewriteFile);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void rewriteFile(final Path file) {
        try {
            final CompilationUnit compilationUnit = StaticJavaParser.parse(file);
            LexicalPreservingPrinter.setup(compilationUnit);

            boolean changed = false;
            changed |= rewriteImports(compilationUnit);
            changed |= rewriteAnnotations(compilationUnit);
            changed |= rewriteTypes(compilationUnit);

            if (changed) {
                Files.write(file, LexicalPreservingPrinter.print(compilationUnit).getBytes(StandardCharsets.UTF_8));
            }
        } catch (IOException e) {
            throw new RuntimeException("failed to rewrite " + file, e);
        }
    }

    private static boolean rewriteImports(final CompilationUnit compilationUnit) {
        boolean changed = false;
        for (final ImportDeclaration importDeclaration : compilationUnit.getImports()) {
            final String name = importDeclaration.getNameAsString();
            if (name.startsWith(OLD_PACKAGE)) {
                importDeclaration.setName(name.replace(OLD_PACKAGE, NEW_PACKAGE));
                changed = true;
            }
        }
        return changed;
    }

    private static boolean rewriteAnnotations(final CompilationUnit compilationUnit) {
        boolean changed = false;
        for (final AnnotationExpr annotationExpr : compilationUnit.findAll(AnnotationExpr.class)) {
            final String name = annotationExpr.getNameAsString();
            if (name.startsWith(OLD_PACKAGE)) {
                annotationExpr.setName(StaticJavaParser.parseName(name.replace(OLD_PACKAGE, NEW_PACKAGE)));
                changed = true;
            }
        }
        return changed;
    }

    private static boolean rewriteTypes(final CompilationUnit compilationUnit) {
        boolean changed = false;
        for (final Type type : compilationUnit.findAll(Type.class)) {
            if (type instanceof ClassOrInterfaceType) {
                final String text = type.toString();
                if (text.startsWith(OLD_PACKAGE)) {
                    type.replace(StaticJavaParser.parseType(text.replace(OLD_PACKAGE, NEW_PACKAGE)));
                    changed = true;
                }
            }
        }
        return changed;
    }
}
