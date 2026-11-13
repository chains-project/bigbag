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
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Main {

    private static final String OLD_TYPE = "eu.europa.esig.dss.pades.CertificationPermission";
    private static final String NEW_TYPE = "eu.europa.esig.dss.enumerations.CertificationPermission";

    public static void main(String[] args) {
        if (args.length == 0) {
            throw new IllegalArgumentException("Expected the source root path as the first argument");
        }

        Path sourceRoot = Paths.get(args[0]).toAbsolutePath().normalize();
        if (!Files.isDirectory(sourceRoot)) {
            throw new IllegalArgumentException("Source root does not exist or is not a directory: " + sourceRoot);
        }

        List<Path> javaFiles;
        try (Stream<Path> stream = Files.walk(sourceRoot)) {
            javaFiles = stream.filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                    .collect(Collectors.toList());
        } catch (IOException e) {
            throw new IllegalStateException("Failed to scan source root: " + sourceRoot, e);
        }

        int updatedFiles = 0;
        for (Path javaFile : javaFiles) {
            if (rewriteFile(javaFile)) {
                updatedFiles++;
            }
        }

        System.out.println("Updated " + updatedFiles + " file(s)");
    }

    private static boolean rewriteFile(Path javaFile) {
        String source;
        try {
            source = Files.readString(javaFile);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read file: " + javaFile, e);
        }

        final CompilationUnit compilationUnit;
        try {
            compilationUnit = StaticJavaParser.parse(source);
        } catch (Exception ex) {
            return false;
        }

        LexicalPreservingPrinter.setup(compilationUnit);

        boolean changed = false;

        for (ImportDeclaration importDeclaration : new ArrayList<>(compilationUnit.findAll(ImportDeclaration.class))) {
            if (OLD_TYPE.equals(importDeclaration.getNameAsString())) {
                importDeclaration.setName(StaticJavaParser.parseName(NEW_TYPE));
                changed = true;
            }
        }

        for (ClassOrInterfaceType type : new ArrayList<>(compilationUnit.findAll(ClassOrInterfaceType.class))) {
            if (OLD_TYPE.equals(type.asString())) {
                type.replace(StaticJavaParser.parseClassOrInterfaceType(NEW_TYPE));
                changed = true;
            }
        }

        if (changed) {
            try {
                Files.writeString(javaFile, LexicalPreservingPrinter.print(compilationUnit));
            } catch (IOException e) {
                throw new IllegalStateException("Failed to write updated file: " + javaFile, e);
            }
        }

        return changed;
    }
}
