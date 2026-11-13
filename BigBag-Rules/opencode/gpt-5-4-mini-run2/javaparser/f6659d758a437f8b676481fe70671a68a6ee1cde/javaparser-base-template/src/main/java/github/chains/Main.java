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
import java.util.stream.Stream;

public class Main {
    private static final String OLD_FQCN = "org.yaml.snakeyaml.inspector.TrustedTagInspector";
    private static final String NEW_FQCN = "org.yaml.snakeyaml.inspector.UnTrustedTagInspector";
    private static final String OLD_SIMPLE = "TrustedTagInspector";
    private static final String NEW_SIMPLE = "UnTrustedTagInspector";

    public static void main(String[] args) {
        if (args.length == 0) {
            throw new IllegalArgumentException("Expected the source root path as the first argument");
        }

        Path sourceRoot = Paths.get(args[0]);
        if (!Files.isDirectory(sourceRoot)) {
            throw new IllegalArgumentException("Source root is not a directory: " + sourceRoot);
        }

        try (Stream<Path> paths = Files.walk(sourceRoot)) {
            paths.filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                .forEach(Main::transformFile);
        } catch (IOException e) {
            throw new RuntimeException("Failed to traverse " + sourceRoot, e);
        }
    }

    private static void transformFile(Path file) {
        try {
            String original = Files.readString(file, StandardCharsets.UTF_8);
            CompilationUnit cu = StaticJavaParser.parse(original);
            LexicalPreservingPrinter.setup(cu);

            boolean changed = false;

            changed |= replaceImports(cu);
            changed |= replaceObjectCreations(cu);
            if (changed) {
                Files.writeString(file, LexicalPreservingPrinter.print(cu), StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to transform " + file, e);
        }
    }

    private static boolean replaceImports(CompilationUnit cu) {
        boolean changed = false;
        List<ImportDeclaration> imports = List.copyOf(cu.getImports());
        for (ImportDeclaration declaration : imports) {
            String name = declaration.getNameAsString();
            if (OLD_FQCN.equals(name) || OLD_SIMPLE.equals(name)) {
                declaration.setName(NEW_FQCN);
                changed = true;
            }
        }
        if (changed && cu.getImports().stream().noneMatch(i -> NEW_FQCN.equals(i.getNameAsString()))) {
            cu.addImport(NEW_FQCN);
        }
        return changed;
    }

    private static boolean replaceObjectCreations(CompilationUnit cu) {
        final boolean[] changed = {false};
        cu.findAll(ObjectCreationExpr.class).forEach(expr -> {
            ClassOrInterfaceType type = expr.getType();
            if (matchesOldType(type)) {
                expr.replace(StaticJavaParser.parseExpression(
                    "new org.yaml.snakeyaml.inspector.UnTrustedTagInspector()"));
                changed[0] = true;
            }
        });
        return changed[0];
    }

    private static boolean matchesOldType(ClassOrInterfaceType type) {
        String text = type.asString();
        return OLD_FQCN.equals(text) || text.endsWith("." + OLD_SIMPLE) || OLD_SIMPLE.equals(type.getNameAsString()) || NEW_SIMPLE.equals(type.getNameAsString()) || text.endsWith("." + NEW_SIMPLE);
    }
}
