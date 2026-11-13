package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.Name;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

public class Main {
    public static void main(String[] args) {
        final Path sourceRoot = Paths.get(args.length > 0 ? args[0] : ".");

        try (Stream<Path> paths = Files.walk(sourceRoot)) {
            paths.filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                    .forEach(Main::transformFile);
        } catch (IOException e) {
            throw new RuntimeException("Failed to walk source tree: " + sourceRoot, e);
        }
    }

    private static void transformFile(final Path file) {
        try {
            final CompilationUnit compilationUnit = StaticJavaParser.parse(file);
            boolean changed = false;

            for (ImportDeclaration importDeclaration : compilationUnit.getImports()) {
                changed |= changedValidationName(importDeclaration::setName, importDeclaration.getName());
            }

            for (AnnotationExpr annotationExpr : compilationUnit.findAll(AnnotationExpr.class)) {
                changed |= changedValidationName(annotationExpr::setName, annotationExpr.getName());
            }

            if (changed) {
                Files.writeString(file, compilationUnit.toString());
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to transform file: " + file, e);
        }
    }

    private static boolean changedValidationName(final java.util.function.Consumer<Name> setter, final Name currentName) {
        final String qualifiedName = currentName.asString();
        if (!qualifiedName.startsWith("javax.validation")) {
            return false;
        }

        setter.accept(StaticJavaParser.parseName(qualifiedName.replaceFirst("^javax\\.validation", "jakarta.validation")));
        return true;
    }
}
