package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.stream.Stream;

public class Main {

    private static final String[][] PACKAGE_RENAMES = {
            {"javax.interceptor", "jakarta.interceptor"},
            {"javax.annotation", "jakarta.annotation"}
    };

    public static void main(String[] args) {
        Path root = Paths.get(args.length > 0 ? args[0] : ".");
        try (Stream<Path> paths = Files.walk(root)) {
            paths.filter(Main::isJavaSource)
                    .sorted(Comparator.naturalOrder())
                    .forEach(Main::rewriteFile);
        } catch (IOException e) {
            throw new RuntimeException("Failed to walk source tree: " + root, e);
        }
    }

    private static boolean isJavaSource(Path path) {
        String normalized = path.toString().replace('\\', '/');
        return normalized.endsWith(".java")
                && !normalized.contains("/.git/")
                && !normalized.contains("/target/");
    }

    private static void rewriteFile(Path file) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(file);
            LexicalPreservingPrinter.setup(cu);

            boolean changed = false;
            changed |= rewriteImports(cu);
            changed |= rewriteQualifiedTypes(cu);

            if (changed) {
                Files.writeString(file, LexicalPreservingPrinter.print(cu), StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to rewrite " + file, e);
        }
    }

    private static boolean rewriteImports(CompilationUnit cu) {
        boolean changed = false;
        for (ImportDeclaration importDeclaration : cu.getImports()) {
            String name = importDeclaration.getNameAsString();
            for (String[] rename : PACKAGE_RENAMES) {
                if (name.startsWith(rename[0])) {
                    importDeclaration.setName(name.replaceFirst("^" + rename[0], rename[1]));
                    changed = true;
                    break;
                }
            }
        }
        return changed;
    }

    private static boolean rewriteQualifiedTypes(CompilationUnit cu) {
        boolean[] changed = {false};
        cu.findAll(ClassOrInterfaceType.class).forEach(type -> {
            String rendered = type.toString();
            for (String[] rename : PACKAGE_RENAMES) {
                if (rendered.startsWith(rename[0])) {
                    ClassOrInterfaceType replacement = StaticJavaParser.parseClassOrInterfaceType(
                            rendered.replaceFirst("^" + rename[0], rename[1]));
                    type.replace(replacement);
                    changed[0] = true;
                    break;
                }
            }
        });

        cu.findAll(AnnotationExpr.class).forEach(annotation -> {
            String rendered = annotation.toString();
            for (String[] rename : PACKAGE_RENAMES) {
                if (rendered.startsWith("@" + rename[0])) {
                    AnnotationExpr replacement = StaticJavaParser.parseAnnotation(
                            rendered.replaceFirst("^@" + rename[0], "@" + rename[1]));
                    annotation.replace(replacement);
                    changed[0] = true;
                    break;
                }
            }
        });

        return changed[0];
    }
}
