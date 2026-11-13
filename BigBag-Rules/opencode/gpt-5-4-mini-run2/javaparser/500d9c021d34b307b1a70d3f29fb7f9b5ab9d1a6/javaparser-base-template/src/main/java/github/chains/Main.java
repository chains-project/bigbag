package github.chains;

import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Main {

    private static final String OLD_PREFIX = "javax.mvc";
    private static final String NEW_PREFIX = "jakarta.mvc";

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            throw new IllegalArgumentException("Expected one argument: source directory path");
        }

        Path sourceDir = Paths.get(args[0]);
        if (!Files.isDirectory(sourceDir)) {
            throw new IllegalArgumentException("Not a directory: " + sourceDir);
        }

        StaticJavaParser.setConfiguration(new ParserConfiguration().setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_11));

        try (var paths = Files.walk(sourceDir)) {
            paths.filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                    .forEach(Main::transformFile);
        }
    }

    private static void transformFile(Path file) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(file);
            LexicalPreservingPrinter.setup(cu);

            boolean changed = false;
            for (ImportDeclaration importDeclaration : cu.getImports()) {
                String name = importDeclaration.getNameAsString();
                String updated = renamePrefix(name);
                if (!updated.equals(name)) {
                    importDeclaration.setName(updated);
                    changed = true;
                }
            }

            if (changed) {
                Files.writeString(file, LexicalPreservingPrinter.print(cu));
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to transform " + file, e);
        }
    }

    private static String renamePrefix(String name) {
        if (name.equals(OLD_PREFIX)) {
            return NEW_PREFIX;
        }
        if (name.startsWith(OLD_PREFIX + ".")) {
            return NEW_PREFIX + name.substring(OLD_PREFIX.length());
        }
        return name;
    }
}
