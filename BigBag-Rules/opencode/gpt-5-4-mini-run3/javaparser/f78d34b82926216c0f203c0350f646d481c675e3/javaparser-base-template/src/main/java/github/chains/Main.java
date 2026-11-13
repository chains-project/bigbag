package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class Main {
    private static final String OLD_PACKAGE_PREFIX = "develop.p2p.lib";
    private static final String NEW_PACKAGE_PREFIX = "tokyo.peya.lib";

    public static void main(String[] args) {
        Path sourceRoot = args.length > 0 ? Paths.get(args[0]) : Paths.get(".");

        try {
            transformProject(sourceRoot);
        } catch (IOException e) {
            throw new RuntimeException("Failed to transform sources under " + sourceRoot, e);
        }
    }

    private static void transformProject(Path sourceRoot) throws IOException {
        if (!Files.isDirectory(sourceRoot)) {
            throw new IllegalArgumentException("Source root does not exist or is not a directory: " + sourceRoot);
        }

        try (var paths = Files.walk(sourceRoot)) {
            paths.filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                .forEach(Main::transformFile);
        }
    }

    private static void transformFile(Path javaFile) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(javaFile, StandardCharsets.UTF_8);
            LexicalPreservingPrinter.setup(cu);

            boolean changed = rewriteImports(cu);
            changed |= rewriteQualifiedReferences(cu);

            if (changed) {
                Files.writeString(javaFile, LexicalPreservingPrinter.print(cu), StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to transform " + javaFile, e);
        }
    }

    private static boolean rewriteImports(CompilationUnit cu) {
        boolean changed = false;
        for (ImportDeclaration importDeclaration : cu.getImports()) {
            String importName = importDeclaration.getNameAsString();
            if (importName.equals(OLD_PACKAGE_PREFIX) || importName.startsWith(OLD_PACKAGE_PREFIX + ".")) {
                importDeclaration.setName(rewriteQualifiedName(importName));
                changed = true;
            }
        }
        return changed;
    }

    private static boolean rewriteQualifiedReferences(CompilationUnit cu) {
        boolean changed = false;

        List<FieldAccessExpr> fieldAccessExprs = new ArrayList<>(cu.findAll(FieldAccessExpr.class));
        for (FieldAccessExpr fieldAccessExpr : fieldAccessExprs) {
            String text = fieldAccessExpr.toString();
            if (startsWithOldPackage(text)) {
                fieldAccessExpr.replace(StaticJavaParser.parseExpression(rewriteQualifiedName(text)));
                changed = true;
            }
        }

        List<ClassOrInterfaceType> classOrInterfaceTypes = new ArrayList<>(cu.findAll(ClassOrInterfaceType.class));
        for (ClassOrInterfaceType type : classOrInterfaceTypes) {
            String text = type.toString();
            if (startsWithOldPackage(text)) {
                type.replace(StaticJavaParser.parseClassOrInterfaceType(rewriteQualifiedName(text)));
                changed = true;
            }
        }

        return changed;
    }

    private static boolean startsWithOldPackage(String text) {
        return text.equals(OLD_PACKAGE_PREFIX) || text.startsWith(OLD_PACKAGE_PREFIX + ".");
    }

    private static String rewriteQualifiedName(String text) {
        if (text.equals(OLD_PACKAGE_PREFIX)) {
            return NEW_PACKAGE_PREFIX;
        }
        if (text.startsWith(OLD_PACKAGE_PREFIX + ".")) {
            return NEW_PACKAGE_PREFIX + text.substring(OLD_PACKAGE_PREFIX.length());
        }
        return text;
    }
}
