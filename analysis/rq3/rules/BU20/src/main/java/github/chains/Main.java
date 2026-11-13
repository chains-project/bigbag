package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class Main {

    private static final String OLD_TYPE = "net.lingala.zip4j.core.ZipFile";

    private static final String NEW_TYPE = "net.lingala.zip4j.ZipFile";

    public static void main(String[] args) {
        final Path sourceRoot = args.length > 0 ? Paths.get(args[0]) : Paths.get(".");
        try {
            rewriteProject(sourceRoot);
        } catch (IOException e) {
            throw new RuntimeException("Failed to rewrite sources under " + sourceRoot, e);
        }
    }

    private static void rewriteProject(Path sourceRoot) throws IOException {
        try (var paths = Files.walk(sourceRoot)) {
            final List<Path> javaFiles = paths
                    .filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                    .collect(Collectors.toList());

            for (Path javaFile : javaFiles) {
                rewriteFile(javaFile);
            }
        }
    }

    private static void rewriteFile(Path javaFile) throws IOException {
        final CompilationUnit compilationUnit = StaticJavaParser.parse(javaFile);
        LexicalPreservingPrinter.setup(compilationUnit);
        boolean changed = false;

        for (ImportDeclaration importDeclaration : compilationUnit.getImports()) {
            if (OLD_TYPE.equals(importDeclaration.getNameAsString())) {
                importDeclaration.setName(NEW_TYPE);
                changed = true;
            }
        }

        for (ClassOrInterfaceType type : compilationUnit.findAll(ClassOrInterfaceType.class)) {
            changed |= rewriteQualifiedType(type);
        }

        for (ObjectCreationExpr creationExpr : compilationUnit.findAll(ObjectCreationExpr.class)) {
            changed |= rewriteQualifiedType(creationExpr.getType());
        }

        if (changed) {
            Files.writeString(javaFile, LexicalPreservingPrinter.print(compilationUnit));
        }
    }

    private static boolean rewriteQualifiedType(ClassOrInterfaceType type) {
        if (!isOldQualifiedType(type)) {
            return false;
        }

        type.replace(StaticJavaParser.parseClassOrInterfaceType(NEW_TYPE));
        return true;
    }

    private static boolean isOldQualifiedType(ClassOrInterfaceType type) {
        if (!"ZipFile".equals(type.getNameAsString())) {
            return false;
        }

        Optional<String> scope = type.getScope().map(Object::toString);
        return scope.map("net.lingala.zip4j.core"::equals).orElse(false)
                || OLD_TYPE.equals(type.toString());
    }
}
