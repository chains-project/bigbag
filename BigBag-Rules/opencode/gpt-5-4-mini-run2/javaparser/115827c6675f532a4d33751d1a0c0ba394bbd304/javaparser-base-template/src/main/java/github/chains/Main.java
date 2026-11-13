package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

public class Main {
    private static final String OLD_PREFIX = "javax.servlet";
    private static final String NEW_PREFIX = "jakarta.servlet";

    public static void main(String[] args) {
        if (args.length == 0) {
            throw new IllegalArgumentException("Expected a source root path");
        }

        Path sourceRoot = Path.of(args[0]);
        try (Stream<Path> paths = Files.walk(sourceRoot)) {
            paths.filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                    .forEach(Main::transformFile);
        } catch (IOException e) {
            throw new RuntimeException("Failed to walk source tree: " + sourceRoot, e);
        }
    }

    private static void transformFile(Path file) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(file);
            LexicalPreservingPrinter.setup(cu);

            boolean changed = rewriteImports(cu);
            changed |= rewriteQualifiedNames(cu);

            if (changed) {
                Files.writeString(file, LexicalPreservingPrinter.print(cu), StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to process " + file, e);
        }
    }

    private static boolean rewriteImports(CompilationUnit cu) {
        boolean changed = false;
        for (ImportDeclaration importDeclaration : cu.getImports()) {
            String name = importDeclaration.getNameAsString();
            if (name.startsWith(OLD_PREFIX)) {
                importDeclaration.setName(StaticJavaParser.parseName(rewritePrefix(name)));
                changed = true;
            }
        }
        return changed;
    }

    private static boolean rewriteQualifiedNames(CompilationUnit cu) {
        boolean changed = false;

        for (ClassOrInterfaceType type : cu.findAll(ClassOrInterfaceType.class)) {
            String text = type.toString();
            if (text.startsWith(OLD_PREFIX)) {
                type.replace(StaticJavaParser.parseClassOrInterfaceType(rewritePrefix(text)));
                changed = true;
            }
        }

        for (FieldAccessExpr fieldAccessExpr : cu.findAll(FieldAccessExpr.class)) {
            String text = fieldAccessExpr.toString();
            if (text.startsWith(OLD_PREFIX)) {
                fieldAccessExpr.replace(StaticJavaParser.parseExpression(rewritePrefix(text)));
                changed = true;
            }
        }

        for (NameExpr nameExpr : cu.findAll(NameExpr.class)) {
            String text = nameExpr.toString();
            if (text.startsWith(OLD_PREFIX)) {
                nameExpr.replace(StaticJavaParser.parseExpression(rewritePrefix(text)));
                changed = true;
            }
        }

        return changed;
    }

    private static String rewritePrefix(String text) {
        return NEW_PREFIX + text.substring(OLD_PREFIX.length());
    }
}
