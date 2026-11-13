package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.Name;
import com.github.javaparser.ast.ImportDeclaration;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

public class Main {
    private static final String OLD_PREFIX = "javax.jws";
    private static final String NEW_PREFIX = "jakarta.jws";

    public static void main(String[] args) {
        if (args.length != 1) {
            throw new IllegalArgumentException("Usage: Main <source-root>");
        }

        Path sourceRoot = Paths.get(args[0]);
        if (!Files.isDirectory(sourceRoot)) {
            throw new IllegalArgumentException("Not a directory: " + sourceRoot);
        }

        AtomicInteger changedFiles = new AtomicInteger();
        try (Stream<Path> paths = Files.walk(sourceRoot)) {
            paths.filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                    .forEach(path -> process(path, changedFiles));
        } catch (IOException e) {
            throw new RuntimeException("Failed to traverse " + sourceRoot, e);
        }

        System.out.println("Updated " + changedFiles.get() + " file(s).");
    }

    private static void process(Path path, AtomicInteger changedFiles) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(path);
            boolean changed = rewriteCompilationUnit(cu);
            if (changed) {
                Files.writeString(path, cu.toString(), StandardCharsets.UTF_8);
                changedFiles.incrementAndGet();
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to rewrite " + path, e);
        }
    }

    private static boolean rewriteCompilationUnit(CompilationUnit cu) {
        boolean changed = false;

        for (ImportDeclaration importDeclaration : cu.getImports()) {
            String name = importDeclaration.getNameAsString();
            if (isOldApiName(name)) {
                importDeclaration.setName(StaticJavaParser.parseName(rewritePrefix(name)));
                changed = true;
            }
        }

        for (AnnotationExpr annotationExpr : cu.findAll(AnnotationExpr.class)) {
            String name = annotationExpr.getNameAsString();
            if (isOldApiName(name)) {
                annotationExpr.setName(StaticJavaParser.parseName(rewritePrefix(name)));
                changed = true;
            }
        }

        for (FieldAccessExpr fieldAccessExpr : cu.findAll(FieldAccessExpr.class)) {
            String text = fieldAccessExpr.toString();
            if (isOldApiName(text)) {
                fieldAccessExpr.replace(StaticJavaParser.parseExpression(rewritePrefix(text)));
                changed = true;
            }
        }

        for (Name name : cu.findAll(Name.class)) {
            String text = name.asString();
            if (isOldApiName(text)) {
                name.replace(StaticJavaParser.parseName(rewritePrefix(text)));
                changed = true;
            }
        }

        return changed;
    }

    private static boolean isOldApiName(String value) {
        return value.equals(OLD_PREFIX) || value.startsWith(OLD_PREFIX + ".");
    }

    private static String rewritePrefix(String value) {
        if (value.equals(OLD_PREFIX)) {
            return NEW_PREFIX;
        }
        if (value.startsWith(OLD_PREFIX + ".")) {
            return NEW_PREFIX + value.substring(OLD_PREFIX.length());
        }
        return value;
    }
}
