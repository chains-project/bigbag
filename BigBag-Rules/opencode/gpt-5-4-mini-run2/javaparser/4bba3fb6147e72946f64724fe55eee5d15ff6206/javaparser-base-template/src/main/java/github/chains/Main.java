package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.Name;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            throw new IllegalArgumentException("Expected source root path as the first argument");
        }
        Path sourceRoot = Paths.get(args[0]);
        try (Stream<Path> paths = Files.walk(sourceRoot)) {
            paths.filter(Main::isJavaSource)
                    .forEach(Main::rewriteFile);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static boolean isJavaSource(Path path) {
        String text = path.toString();
        return Files.isRegularFile(path)
                && text.endsWith(".java")
                && !text.contains("/target/")
                && !text.contains("/.git/");
    }

    private static void rewriteFile(Path file) {
        try {
            CompilationUnit compilationUnit = StaticJavaParser.parse(file);
            LexicalPreservingPrinter.setup(compilationUnit);

            boolean changed = false;

            for (ImportDeclaration importDeclaration : compilationUnit.getImports()) {
                changed |= rewriteName(importDeclaration::getName, importDeclaration::setName);
            }

            for (AnnotationExpr annotationExpr : compilationUnit.findAll(AnnotationExpr.class)) {
                changed |= rewriteName(annotationExpr::getName, annotationExpr::setName);
            }

            if (changed) {
                Files.write(file, LexicalPreservingPrinter.print(compilationUnit).getBytes(StandardCharsets.UTF_8));
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to rewrite " + file, e);
        }
    }

    private static boolean rewriteName(java.util.function.Supplier<Name> getter,
                                       java.util.function.Consumer<Name> setter) {
        Name current = getter.get();
        String original = current.asString();
        if (!original.startsWith("javax.annotation")) {
            return false;
        }
        String replacement = "jakarta.annotation" + original.substring("javax.annotation".length());
        setter.accept(StaticJavaParser.parseName(replacement));
        return true;
    }
}
