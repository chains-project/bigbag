package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

public class Main {
    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Usage: Main <source-root>");
            System.exit(1);
        }

        Path sourceRoot = Paths.get(args[0]);
        try (Stream<Path> paths = Files.walk(sourceRoot)) {
            paths.filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                    .forEach(Main::transformFile);
        } catch (IOException e) {
            throw new RuntimeException("Failed to traverse " + sourceRoot, e);
        }
    }

    private static void transformFile(Path file) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(file);
            LexicalPreservingPrinter.setup(cu);
            boolean changed = false;

            for (FieldAccessExpr fieldAccess : cu.findAll(FieldAccessExpr.class)) {
                if (isCompareStatusAccess(fieldAccess)) {
                    fieldAccess.replace(new MethodCallExpr(fieldAccess.getScope().clone(), "getStatus"));
                    changed = true;
                }
            }

            if (changed) {
                Files.writeString(file, LexicalPreservingPrinter.print(cu), StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to transform " + file, e);
        }
    }

    private static boolean isCompareStatusAccess(FieldAccessExpr fieldAccess) {
        return "status".equals(fieldAccess.getNameAsString())
                && fieldAccess.getScope() instanceof MethodCallExpr
                && "getCompare".equals(((MethodCallExpr) fieldAccess.getScope()).getNameAsString());
    }
}
