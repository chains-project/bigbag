package github.chains;

import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.expr.Name;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Main {
    private static final String OLD_PREFIX = "javax.validation";
    private static final String NEW_PREFIX = "jakarta.validation";

    public static void main(String[] args) {
        if (args.length == 0) {
            throw new IllegalArgumentException("Expected a source directory path");
        }

        Path sourceRoot = Paths.get(args[0]);
        if (!Files.isDirectory(sourceRoot)) {
            throw new IllegalArgumentException("Not a directory: " + sourceRoot);
        }

        StaticJavaParser.getConfiguration().setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_11);

        try (Stream<Path> paths = Files.walk(sourceRoot)) {
            List<Path> javaFiles = paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .collect(Collectors.toList());

            for (Path javaFile : javaFiles) {
                rewriteFile(javaFile);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to traverse source tree: " + sourceRoot, e);
        }
    }

    private static void rewriteFile(Path javaFile) {
        try {
            CompilationUnit compilationUnit = StaticJavaParser.parse(javaFile);
            LexicalPreservingPrinter.setup(compilationUnit);

            boolean changed = false;
            changed |= rewriteImports(compilationUnit);
            changed |= rewriteNames(compilationUnit);
            changed |= rewriteQualifiedTypes(compilationUnit);

            if (changed) {
                Files.writeString(javaFile, LexicalPreservingPrinter.print(compilationUnit));
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to transform " + javaFile, e);
        }
    }

    private static boolean rewriteImports(CompilationUnit compilationUnit) {
        boolean changed = false;
        for (ImportDeclaration importDeclaration : compilationUnit.getImports()) {
            String importedName = importDeclaration.getNameAsString();
            if (importedName.startsWith(OLD_PREFIX)) {
                importDeclaration.setName(rewriteName(importedName));
                changed = true;
            }
        }
        return changed;
    }

    private static boolean rewriteNames(CompilationUnit compilationUnit) {
        boolean changed = false;
        for (Name name : compilationUnit.findAll(Name.class)) {
            String value = name.asString();
            if (value.startsWith(OLD_PREFIX)) {
                name.replace(StaticJavaParser.parseName(rewriteName(value)));
                changed = true;
            }
        }
        return changed;
    }

    private static boolean rewriteQualifiedTypes(CompilationUnit compilationUnit) {
        boolean changed = false;
        for (ClassOrInterfaceType type : compilationUnit.findAll(ClassOrInterfaceType.class)) {
            String value = type.asString();
            if (value.startsWith(OLD_PREFIX)) {
                type.replace(StaticJavaParser.parseClassOrInterfaceType(rewriteName(value)));
                changed = true;
            }
        }
        return changed;
    }

    private static String rewriteName(String value) {
        return NEW_PREFIX + value.substring(OLD_PREFIX.length());
    }
}
