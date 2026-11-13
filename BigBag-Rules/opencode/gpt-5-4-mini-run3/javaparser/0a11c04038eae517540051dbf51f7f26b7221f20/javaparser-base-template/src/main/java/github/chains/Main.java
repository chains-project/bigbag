package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

public class Main {
    private static final String TARGET_CONSTRUCTOR = "org.yaml.snakeyaml.constructor.Constructor";
    private static final String LOADER_OPTIONS = "org.yaml.snakeyaml.LoaderOptions";

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            throw new IllegalArgumentException("Usage: Main <source-root>");
        }

        Path root = Paths.get(args[0]).toAbsolutePath().normalize();
        try (Stream<Path> paths = Files.walk(root, Integer.MAX_VALUE, FileVisitOption.FOLLOW_LINKS)) {
            paths.filter(Main::isJavaSource)
                    .forEach(Main::transform);
        }
    }

    private static boolean isJavaSource(Path path) {
        String normalized = path.toString().replace('\\', '/');
        if (!normalized.endsWith(".java")) {
            return false;
        }
        return !normalized.contains("/target/") && !normalized.contains("/build/") && !normalized.contains("/.git/");
    }

    private static void transform(Path sourceFile) {
        try {
            CompilationUnit compilationUnit = StaticJavaParser.parse(sourceFile);
            LexicalPreservingPrinter.setup(compilationUnit);

            boolean changed = false;
            for (ObjectCreationExpr objectCreationExpr : compilationUnit.findAll(ObjectCreationExpr.class)) {
                if (isTargetConstructor(objectCreationExpr, compilationUnit) && objectCreationExpr.getArguments().size() == 1) {
                    objectCreationExpr.addArgument(newLoaderOptionsExpression());
                    changed = true;
                }
            }

            if (changed) {
                Files.writeString(sourceFile, LexicalPreservingPrinter.print(compilationUnit), StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to transform " + sourceFile, e);
        }
    }

    private static boolean isTargetConstructor(ObjectCreationExpr objectCreationExpr, CompilationUnit compilationUnit) {
        if (!"Constructor".equals(objectCreationExpr.getType().getNameAsString())) {
            return false;
        }

        String typeText = objectCreationExpr.getType().toString();
        if (TARGET_CONSTRUCTOR.equals(typeText) || typeText.endsWith(".Constructor")) {
            return true;
        }

        return compilationUnit.getImports().stream().anyMatch(importDeclaration ->
                !importDeclaration.isAsterisk() && TARGET_CONSTRUCTOR.equals(importDeclaration.getNameAsString())
                        || importDeclaration.isAsterisk() && "org.yaml.snakeyaml.constructor".equals(importDeclaration.getNameAsString()));
    }

    private static Expression newLoaderOptionsExpression() {
        return StaticJavaParser.parseExpression("new " + LOADER_OPTIONS + "()");
    }
}
