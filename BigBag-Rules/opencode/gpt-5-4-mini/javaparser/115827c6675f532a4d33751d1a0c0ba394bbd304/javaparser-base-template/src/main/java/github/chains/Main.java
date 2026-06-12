package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.Name;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

public class Main {

    private static final String OLD_PREFIX = "javax.servlet";
    private static final String NEW_PREFIX = "jakarta.servlet";

    public static void main(String[] args) throws IOException {
        Path sourceRoot = Paths.get(args.length > 0 ? args[0] : ".").toAbsolutePath().normalize();

        try (Stream<Path> paths = Files.walk(sourceRoot)) {
            paths.filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                    .forEach(Main::rewriteFile);
        }
    }

    private static void rewriteFile(Path path) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(path, StandardCharsets.UTF_8);
            LexicalPreservingPrinter.setup(cu);

            boolean changed = false;

            for (ImportDeclaration importDeclaration : cu.getImports()) {
                changed |= replaceImport(importDeclaration);
            }

            changed |= rewriteNodes(cu);

            if (changed) {
                Files.writeString(path, LexicalPreservingPrinter.print(cu), StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to process " + path, e);
        }
    }

    private static boolean rewriteNodes(Node node) {
        boolean changed = false;

        for (Node child : node.getChildNodes()) {
            changed |= rewriteNodes(child);
        }

        if (node instanceof Name) {
            changed |= replaceName((Name) node);
        } else if (node instanceof ClassOrInterfaceType) {
            changed |= replaceClassOrInterfaceType((ClassOrInterfaceType) node);
        }

        return changed;
    }

    private static boolean replaceImport(ImportDeclaration importDeclaration) {
        String name = importDeclaration.getNameAsString();
        if (name.startsWith(OLD_PREFIX)) {
            importDeclaration.setName(StaticJavaParser.parseName(rewritePrefix(name)));
            return true;
        }
        return false;
    }

    private static boolean replaceName(Name name) {
        String value = name.asString();
        if (value.startsWith(OLD_PREFIX)) {
            name.replace(StaticJavaParser.parseName(rewritePrefix(value)));
            return true;
        }
        return false;
    }

    private static boolean replaceClassOrInterfaceType(ClassOrInterfaceType type) {
        String value = type.asString();
        if (value.startsWith(OLD_PREFIX)) {
            type.replace(StaticJavaParser.parseClassOrInterfaceType(rewritePrefix(value)));
            return true;
        }
        return false;
    }

    private static String rewritePrefix(String value) {
        return NEW_PREFIX + value.substring(OLD_PREFIX.length());
    }

    private static String rewriteSuffix(String value) {
        return NEW_PREFIX + value.substring(OLD_PREFIX.length());
    }
}
