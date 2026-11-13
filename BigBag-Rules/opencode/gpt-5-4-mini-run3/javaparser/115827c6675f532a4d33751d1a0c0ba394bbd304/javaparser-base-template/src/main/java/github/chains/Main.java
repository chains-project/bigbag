package github.chains;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;

public class Main {

    private static final String OLD_PACKAGE = "javax.servlet";
    private static final String NEW_PACKAGE = "jakarta.servlet";

    public static void main(String[] args) throws IOException {
        Path sourceRoot = args.length > 0 ? Paths.get(args[0]) : Paths.get(".");
        if (!Files.isDirectory(sourceRoot)) {
            throw new IllegalArgumentException("Source root must be a directory: " + sourceRoot);
        }

        try (Stream<Path> paths = Files.walk(sourceRoot)) {
            List<Path> javaFiles = paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .collect(Collectors.toList());

            for (Path file : javaFiles) {
                rewriteFile(file);
            }
        }
    }

    private static void rewriteFile(Path file) throws IOException {
        CompilationUnit compilationUnit = StaticJavaParser.parse(file);
        LexicalPreservingPrinter.setup(compilationUnit);

        boolean changed = false;
        changed |= rewriteImports(compilationUnit);
        changed |= rewriteQualifiedTypes(compilationUnit);
        changed |= rewriteQualifiedFieldAccesses(compilationUnit);

        if (changed) {
            Files.writeString(file, LexicalPreservingPrinter.print(compilationUnit));
        }
    }

    private static boolean rewriteImports(CompilationUnit compilationUnit) {
        boolean changed = false;
        for (ImportDeclaration importDeclaration : compilationUnit.getImports()) {
            String importName = importDeclaration.getNameAsString();
            if (importName.startsWith(OLD_PACKAGE)) {
                importDeclaration.setName(StaticJavaParser.parseName(importName.replace(OLD_PACKAGE, NEW_PACKAGE)));
                changed = true;
            }
        }
        return changed;
    }

    private static boolean rewriteQualifiedTypes(CompilationUnit compilationUnit) {
        boolean changed = false;
        for (ClassOrInterfaceType type : compilationUnit.findAll(ClassOrInterfaceType.class)) {
            String typeText = type.toString();
            if (typeText.startsWith(OLD_PACKAGE)) {
                type.replace(StaticJavaParser.parseClassOrInterfaceType(typeText.replace(OLD_PACKAGE, NEW_PACKAGE)));
                changed = true;
            }
        }
        return changed;
    }

    private static boolean rewriteQualifiedFieldAccesses(CompilationUnit compilationUnit) {
        boolean changed = false;
        for (FieldAccessExpr fieldAccessExpr : compilationUnit.findAll(FieldAccessExpr.class)) {
            String fieldAccessText = fieldAccessExpr.toString();
            if (fieldAccessText.startsWith(OLD_PACKAGE)) {
                fieldAccessExpr.replace(StaticJavaParser.parseExpression(fieldAccessText.replace(OLD_PACKAGE, NEW_PACKAGE)));
                changed = true;
            }
        }
        return changed;
    }
}
