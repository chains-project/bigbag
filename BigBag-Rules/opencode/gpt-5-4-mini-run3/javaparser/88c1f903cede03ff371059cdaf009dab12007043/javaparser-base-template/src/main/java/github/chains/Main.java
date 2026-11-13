package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.stream.Stream;

public class Main {

    private static final String OLD_ZIPFILE_FQN = "net.lingala.zip4j.core.ZipFile";
    private static final String NEW_ZIPFILE_FQN = "net.lingala.zip4j.ZipFile";

    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            throw new IllegalArgumentException("Usage: Main <source-root>");
        }

        final Path sourceRoot = Paths.get(args[0]).toAbsolutePath().normalize();
        final JavaParser parser = new JavaParser(new ParserConfiguration());

        try (Stream<Path> paths = Files.walk(sourceRoot)) {
            paths.filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                    .sorted(Comparator.naturalOrder())
                    .forEach(path -> transformFile(parser, path));
        }
    }

    private static void transformFile(JavaParser parser, Path path) {
        try {
            final CompilationUnit cu = parser.parse(path).getResult()
                    .orElseThrow(() -> new IllegalStateException("Could not parse " + path));
            LexicalPreservingPrinter.setup(cu);

            boolean changed = false;
            for (ImportDeclaration importDeclaration : cu.findAll(ImportDeclaration.class)) {
                if (OLD_ZIPFILE_FQN.equals(importDeclaration.getNameAsString())) {
                    importDeclaration.setName(NEW_ZIPFILE_FQN);
                    changed = true;
                }
            }

            for (ClassOrInterfaceType type : cu.findAll(ClassOrInterfaceType.class)) {
                changed |= rewriteType(type);
            }

            for (ObjectCreationExpr expr : cu.findAll(ObjectCreationExpr.class)) {
                changed |= rewriteObjectCreation(expr);
            }

            if (changed) {
                Files.writeString(path, LexicalPreservingPrinter.print(cu));
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to transform " + path, e);
        }
    }

    private static boolean rewriteType(ClassOrInterfaceType type) {
        if (matchesOldQualifiedName(type.getScope().map(Object::toString).orElse(null), type.getNameAsString())) {
            type.setScope(StaticJavaParser.parseClassOrInterfaceType("net.lingala.zip4j"));
            type.setName("ZipFile");
            return true;
        }
        return false;
    }

    private static boolean rewriteObjectCreation(ObjectCreationExpr expr) {
        final ClassOrInterfaceType type = expr.getType();
        if (matchesOldQualifiedName(type.getScope().map(Object::toString).orElse(null), type.getNameAsString())) {
            type.setScope(StaticJavaParser.parseClassOrInterfaceType("net.lingala.zip4j"));
            type.setName("ZipFile");
            return true;
        }
        return false;
    }

    private static boolean matchesOldQualifiedName(String scope, String name) {
        return "ZipFile".equals(name) && "net.lingala.zip4j.core".equals(scope);
    }
}
