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
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class Main {
    private static final List<Replacement> TYPE_REPLACEMENTS = List.of(
            new Replacement(
                    "com.mysql.jdbc.exceptions.MySQLTimeoutException",
                    "com.mysql.cj.jdbc.exceptions.MySQLTimeoutException"
            )
    );

    public static void main(String[] args) {
        final Path sourceRoot = args.length > 0 ? Paths.get(args[0]) : Paths.get(".");

        try {
            Files.walk(sourceRoot)
                    .filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                    .forEach(Main::transformFile);
        } catch (IOException e) {
            throw new RuntimeException("Failed to traverse source root: " + sourceRoot, e);
        }
    }

    private static void transformFile(final Path file) {
        try {
            final CompilationUnit compilationUnit = StaticJavaParser.parse(file);
            final boolean[] changed = new boolean[] { false };

            LexicalPreservingPrinter.setup(compilationUnit);

            for (Replacement replacement : TYPE_REPLACEMENTS) {
                changed[0] |= replaceImport(compilationUnit, replacement.oldType, replacement.newType);
                changed[0] |= replaceQualifiedTypes(compilationUnit, replacement.oldType, replacement.newType);
            }

            if (changed[0]) {
                Files.writeString(file, LexicalPreservingPrinter.print(compilationUnit), StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to transform file: " + file, e);
        }
    }

    private static boolean replaceImport(final CompilationUnit compilationUnit, final String oldType, final String newType) {
        final String oldPackage = packageName(oldType);
        final String newPackage = packageName(newType);
        final String simpleName = simpleName(oldType);
        boolean changed = false;

        for (ImportDeclaration importDeclaration : compilationUnit.getImports()) {
            if (importDeclaration.getNameAsString().equals(oldType)) {
                importDeclaration.setName(StaticJavaParser.parseName(newType));
                changed = true;
            } else if (importDeclaration.getNameAsString().equals(oldPackage + "." + simpleName)) {
                importDeclaration.setName(StaticJavaParser.parseName(newPackage + "." + simpleName));
                changed = true;
            }
        }

        return changed;
    }

    private static boolean replaceQualifiedTypes(final CompilationUnit compilationUnit, final String oldType, final String newType) {
        final List<ClassOrInterfaceType> matches = compilationUnit.findAll(ClassOrInterfaceType.class).stream()
                .filter(type -> type.toString().equals(oldType))
                .collect(Collectors.toList());

        boolean changed = false;
        for (ClassOrInterfaceType match : matches) {
            match.replace(StaticJavaParser.parseClassOrInterfaceType(newType));
            changed = true;
        }
        return changed;
    }

    private static String packageName(final String fqcn) {
        return fqcn.substring(0, fqcn.lastIndexOf('.'));
    }

    private static String simpleName(final String fqcn) {
        return fqcn.substring(fqcn.lastIndexOf('.') + 1);
    }

    private static final class Replacement {
        private final String oldType;
        private final String newType;

        private Replacement(final String oldType, final String newType) {
            this.oldType = Objects.requireNonNull(oldType);
            this.newType = Objects.requireNonNull(newType);
        }
    }
}
