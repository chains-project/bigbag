package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Main {
    public static void main(String[] args) {
        if (args.length != 1) {
            throw new IllegalArgumentException("Expected one argument: source directory");
        }

        Path root = Paths.get(args[0]);
        if (!Files.isDirectory(root)) {
            throw new IllegalArgumentException("Source directory does not exist: " + root);
        }

        try {
            transform(root);
        } catch (IOException e) {
            throw new RuntimeException("Failed to transform sources", e);
        }
    }

    private static void transform(Path root) throws IOException {
        try (var paths = Files.walk(root)) {
            paths.filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                    .forEach(Main::rewriteFileSafely);
        }
    }

    private static void rewriteFileSafely(Path file) {
        try {
            rewriteFile(file);
        } catch (Exception e) {
            throw new RuntimeException("Failed to rewrite " + file, e);
        }
    }

    private static void rewriteFile(Path file) throws IOException {
        CompilationUnit cu = StaticJavaParser.parse(file);
        LexicalPreservingPrinter.setup(cu);

        boolean changed = false;
        for (ObjectCreationExpr creation : cu.findAll(ObjectCreationExpr.class)) {
            if (isSnakeYamlConstructorCreation(creation)) {
                creation.addArgument(StaticJavaParser.parseExpression("new org.yaml.snakeyaml.LoaderOptions()"));
                changed = true;
            }
        }

        if (changed) {
            Files.writeString(file, LexicalPreservingPrinter.print(cu), StandardCharsets.UTF_8);
        }
    }

    private static boolean isSnakeYamlConstructorCreation(ObjectCreationExpr creation) {
        if (!"Constructor".equals(creation.getType().getNameAsString())
                && !"org.yaml.snakeyaml.constructor.Constructor".equals(creation.getType().toString())) {
            return false;
        }
        for (Expression argument : creation.getArguments()) {
            if (argument.toString().contains("LoaderOptions")) {
                return false;
            }
        }
        return true;
    }
}
