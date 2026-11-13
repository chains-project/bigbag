package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

public class Main {

    private static final String TARGET_CONSTRUCTOR = "org.yaml.snakeyaml.constructor.Constructor";
    private static final String TARGET_CONSTRUCTOR_PACKAGE = "org.yaml.snakeyaml.constructor";
    private static final String LOADER_OPTIONS = "org.yaml.snakeyaml.LoaderOptions";

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            throw new IllegalArgumentException("Usage: Main <source-root>");
        }

        Path sourceRoot = Paths.get(args[0]);
        JavaParser parser = new JavaParser(new ParserConfiguration());

        try (Stream<Path> paths = Files.walk(sourceRoot)) {
            paths.filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                    .forEach(path -> transformFile(parser, path));
        }
    }

    private static void transformFile(JavaParser parser, Path path) {
        try {
            String source = Files.readString(path, StandardCharsets.UTF_8);
            CompilationUnit cu = parser.parse(source).getResult().orElse(null);
            if (cu == null) {
                return;
            }

            LexicalPreservingPrinter.setup(cu);
            boolean changed = false;

            for (ObjectCreationExpr creation : cu.findAll(ObjectCreationExpr.class)) {
                if (isTargetConstructorCall(cu, creation) && creation.getArguments().size() == 1) {
                    NodeList<com.github.javaparser.ast.expr.Expression> args = new NodeList<>(creation.getArguments());
                    args.add(parseExpression("new " + LOADER_OPTIONS + "()"));
                    creation.setArguments(args);
                    changed = true;
                }
            }

            if (changed) {
                Files.writeString(path, LexicalPreservingPrinter.print(cu), StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to transform " + path, e);
        }
    }

    private static boolean isTargetConstructorCall(CompilationUnit cu, ObjectCreationExpr creation) {
        String typeName = creation.getType().getNameAsString();
        if (!"Constructor".equals(typeName)) {
            return false;
        }

        String qualifiedType = creation.getType().toString();
        if (TARGET_CONSTRUCTOR.equals(qualifiedType)) {
            return true;
        }

        return cu.getImports().stream().anyMatch(importDecl ->
                (!importDecl.isAsterisk() && TARGET_CONSTRUCTOR.equals(importDecl.getNameAsString()))
                        || (importDecl.isAsterisk() && TARGET_CONSTRUCTOR_PACKAGE.equals(importDecl.getNameAsString())));
    }

    private static com.github.javaparser.ast.expr.Expression parseExpression(String code) {
        return new JavaParser().parseExpression(code).getResult()
                .orElseThrow(() -> new IllegalArgumentException("Unable to parse expression: " + code));
    }
}
