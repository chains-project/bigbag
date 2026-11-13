package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

public class Main {

    private static final String OLD_PREFIX = "javax.";
    private static final String NEW_PREFIX = "jakarta.";

    public static void main(String[] args) {
        if (args.length != 1) {
            throw new IllegalArgumentException("Expected one source root argument");
        }

        Path sourceRoot = Paths.get(args[0]);
        if (!Files.isDirectory(sourceRoot)) {
            throw new IllegalArgumentException("Not a directory: " + sourceRoot);
        }

        try (Stream<Path> paths = Files.walk(sourceRoot)) {
            paths.filter(p -> Files.isRegularFile(p) && p.toString().endsWith(".java"))
                    .forEach(Main::transform);
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    private static void transform(Path file) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(file);
            LexicalPreservingPrinter.setup(cu);

            cu.getImports().forEach(imp -> {
                String name = imp.getNameAsString();
                if (name.startsWith(OLD_PREFIX)) {
                    imp.setName(rewrite(name));
                }
            });

            cu.findAll(AnnotationExpr.class).forEach(annotation -> {
                String name = annotation.getNameAsString();
                if (name.startsWith(OLD_PREFIX)) {
                    annotation.setName(StaticJavaParser.parseName(rewrite(name)));
                }
            });

            cu.findAll(ClassOrInterfaceType.class).forEach(type -> {
                String text = type.asString();
                if (text.startsWith(OLD_PREFIX)) {
                    type.replace(StaticJavaParser.parseClassOrInterfaceType(rewrite(text)));
                }
            });

            cu.findAll(NameExpr.class).forEach(nameExpr -> {
                String text = nameExpr.toString();
                if (text.startsWith(OLD_PREFIX)) {
                    nameExpr.replace(StaticJavaParser.parseExpression(rewrite(text)));
                }
            });

            Files.writeString(file, LexicalPreservingPrinter.print(cu), StandardCharsets.UTF_8);
        } catch (Exception ex) {
            throw new RuntimeException("Failed to transform " + file, ex);
        }
    }

    private static String rewrite(String value) {
        return value.startsWith(OLD_PREFIX) ? NEW_PREFIX + value.substring(OLD_PREFIX.length()) : value;
    }
}
