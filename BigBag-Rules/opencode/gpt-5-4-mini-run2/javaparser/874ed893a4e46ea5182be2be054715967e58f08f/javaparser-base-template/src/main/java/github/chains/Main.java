package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

public class Main {
    private static final String OLD_FQN = "com.fasterxml.jackson.core.JsonProcessingException";
    private static final String NEW_FQN = "com.fasterxml.jackson.core.JacksonException";
    private static final String OLD_SIMPLE = "JsonProcessingException";
    private static final String NEW_SIMPLE = "JacksonException";

    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            throw new IllegalArgumentException("Usage: Main <source-dir> [target-dir]");
        }

        Path sourceDir = Paths.get(args[0]).toAbsolutePath().normalize();
        Path targetDir = args.length > 1 ? Paths.get(args[1]).toAbsolutePath().normalize() : sourceDir;

        ParserConfiguration configuration = new ParserConfiguration();
        StaticJavaParser.setConfiguration(configuration);
        JavaParser parser = new JavaParser(configuration);

        Files.walkFileTree(sourceDir, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (!file.toString().endsWith(".java")) {
                    return FileVisitResult.CONTINUE;
                }

                ParseResult<CompilationUnit> result = parser.parse(file);
                if (result.getResult().isEmpty()) {
                    return FileVisitResult.CONTINUE;
                }

                CompilationUnit cu = result.getResult().get();
                LexicalPreservingPrinter.setup(cu);

                if (rewrite(cu)) {
                    Path relative = sourceDir.relativize(file);
                    Path output = targetDir.resolve(relative);
                    Files.createDirectories(output.getParent());
                    Files.writeString(output, LexicalPreservingPrinter.print(cu), StandardCharsets.UTF_8);
                }

                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static boolean rewrite(CompilationUnit cu) {
        boolean changed = false;

        for (ImportDeclaration importDeclaration : cu.getImports()) {
            if (OLD_FQN.equals(importDeclaration.getNameAsString())) {
                importDeclaration.setName(NEW_FQN);
                changed = true;
            }
        }

        for (ClassOrInterfaceType type : cu.findAll(ClassOrInterfaceType.class)) {
            String typeText = type.asString();
            if (OLD_SIMPLE.equals(typeText) || OLD_FQN.equals(typeText)) {
                type.setName(NEW_SIMPLE);
                changed = true;
            }
        }

        return changed;
    }
}
