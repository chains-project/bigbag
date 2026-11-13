package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Main {

    private static final String OLD_TYPE = "net.lingala.zip4j.core.ZipFile";
    private static final String NEW_TYPE = "net.lingala.zip4j.ZipFile";

    public static void main(String[] args) throws IOException {
        if (args.length == 0) {
            throw new IllegalArgumentException("Expected a source directory path as the first argument");
        }

        final Path sourceRoot = Paths.get(args[0]);
        try (Stream<Path> paths = Files.walk(sourceRoot)) {
            final List<Path> javaFiles = paths
                    .filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                    .collect(Collectors.toList());

            for (Path javaFile : javaFiles) {
                rewriteFile(javaFile);
            }
        }
    }

    private static void rewriteFile(Path javaFile) throws IOException {
        final String source = Files.readString(javaFile, StandardCharsets.UTF_8);
        final CompilationUnit compilationUnit = StaticJavaParser.parse(source);
        LexicalPreservingPrinter.setup(compilationUnit);

        boolean changed = false;

        for (ImportDeclaration importDeclaration : compilationUnit.findAll(ImportDeclaration.class)) {
            if (OLD_TYPE.equals(importDeclaration.getNameAsString())) {
                importDeclaration.setName(StaticJavaParser.parseName(NEW_TYPE));
                changed = true;
            }
        }

        final ClassOrInterfaceType replacementType =
                StaticJavaParser.parseClassOrInterfaceType(NEW_TYPE);

        for (ClassOrInterfaceType type : compilationUnit.findAll(ClassOrInterfaceType.class)) {
            if (matchesOldType(type)) {
                type.setName(replacementType.getName());
                type.setScope(replacementType.getScope().orElse(null));
                changed = true;
            }
        }

        for (ObjectCreationExpr creationExpr : compilationUnit.findAll(ObjectCreationExpr.class)) {
            if (matchesOldType(creationExpr.getType())) {
                creationExpr.setType(replacementType.clone());
                changed = true;
            }
        }

        if (changed) {
            Files.writeString(javaFile, LexicalPreservingPrinter.print(compilationUnit), StandardCharsets.UTF_8);
        }
    }

    private static boolean matchesOldType(ClassOrInterfaceType type) {
        return OLD_TYPE.equals(type.asString()) || OLD_TYPE.equals(type.getNameWithScope());
    }
}
