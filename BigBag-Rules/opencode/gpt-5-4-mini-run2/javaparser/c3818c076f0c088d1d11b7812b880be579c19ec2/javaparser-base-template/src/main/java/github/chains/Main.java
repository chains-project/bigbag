package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.NameExpr;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class Main {
    private static final String OLD_PACKAGE = "javax.validation";
    private static final String NEW_PACKAGE = "jakarta.validation";

    public static void main(String[] args) {
        if (args.length < 1) {
            throw new IllegalArgumentException("Usage: Main <source-root>");
        }

        final Path sourceRoot = Paths.get(args[0]);
        try {
            final List<Path> javaFiles = Files.walk(sourceRoot)
                .filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                .sorted(Comparator.naturalOrder())
                .collect(Collectors.toList());

            for (final Path file : javaFiles) {
                transformFile(file);
            }
        } catch (final IOException e) {
            throw new RuntimeException("Failed to walk source root: " + sourceRoot, e);
        }
    }

    private static void transformFile(final Path file) {
        try {
            final CompilationUnit cu = StaticJavaParser.parse(file, StandardCharsets.UTF_8);
            final boolean changed = rewriteValidationNamespace(cu);
            if (changed) {
                Files.write(file, cu.toString().getBytes(StandardCharsets.UTF_8));
            }
        } catch (final Exception e) {
            throw new RuntimeException("Failed to transform file: " + file, e);
        }
    }

    private static boolean rewriteValidationNamespace(final CompilationUnit cu) {
        boolean changed = false;

        for (final ImportDeclaration importDeclaration : cu.findAll(ImportDeclaration.class)) {
            final String importedName = importDeclaration.getNameAsString();
            if (importedName.equals(OLD_PACKAGE) || importedName.startsWith(OLD_PACKAGE + ".")) {
                importDeclaration.setName(importedName.replaceFirst("^" + OLD_PACKAGE, NEW_PACKAGE));
                changed = true;
            }
        }

        for (final AnnotationExpr annotationExpr : cu.findAll(AnnotationExpr.class)) {
            changed |= rewriteText(annotationExpr.getNameAsString(), annotationExpr::setName);
        }

        for (final NameExpr nameExpr : cu.findAll(NameExpr.class)) {
            changed |= rewriteText(nameExpr.getNameAsString(), nameExpr::setName);
        }

        return changed;
    }

    private static boolean rewriteText(final String value, final java.util.function.Consumer<String> setter) {
        if (value.equals(OLD_PACKAGE) || value.startsWith(OLD_PACKAGE + ".")) {
            setter.accept(value.replaceFirst("^" + OLD_PACKAGE, NEW_PACKAGE));
            return true;
        }

        return false;
    }
}
