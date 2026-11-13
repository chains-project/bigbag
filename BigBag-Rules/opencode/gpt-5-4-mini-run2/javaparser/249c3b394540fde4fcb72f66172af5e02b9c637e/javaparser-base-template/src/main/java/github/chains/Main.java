package github.chains;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;
import com.github.javaparser.StaticJavaParser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class Main {
    private static final String TARGET_METHOD = "getProperties";
    private static final String TARGET_EXCEPTION = "IntrospectionException";
    private static final String TARGET_SUPER_TYPE = "Representer";
    private static final String TARGET_SUPER_TYPE_FQCN = "org.yaml.snakeyaml.representer.Representer";

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            throw new IllegalArgumentException("Expected a single source root path");
        }

        Path sourceRoot = Paths.get(args[0]);
        if (!Files.isDirectory(sourceRoot)) {
            throw new IllegalArgumentException("Source root does not exist: " + sourceRoot);
        }

        Files.walk(sourceRoot)
            .filter(path -> path.toString().endsWith(".java"))
            .forEach(Main::rewriteFile);
    }

    private static void rewriteFile(Path path) {
        try {
            CompilationUnit compilationUnit = StaticJavaParser.parse(path);
            LexicalPreservingPrinter.setup(compilationUnit);

            boolean changed = false;
            for (MethodDeclaration method : compilationUnit.findAll(MethodDeclaration.class)) {
                if (shouldRemoveThrowsClause(method)) {
                    method.getThrownExceptions().removeIf(type -> type.asString().endsWith(TARGET_EXCEPTION));
                    changed = true;
                }
            }

            if (changed) {
                Files.writeString(path, LexicalPreservingPrinter.print(compilationUnit));
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to rewrite " + path, e);
        }
    }

    private static boolean shouldRemoveThrowsClause(MethodDeclaration method) {
        if (!TARGET_METHOD.equals(method.getNameAsString())) {
            return false;
        }
        if (method.getParameters().size() != 1) {
            return false;
        }
        if (method.getThrownExceptions().stream().noneMatch(type -> type.asString().endsWith(TARGET_EXCEPTION))) {
            return false;
        }

        return method.findAncestor(ClassOrInterfaceDeclaration.class)
            .map(Main::extendsTargetRepresenter)
            .orElse(false);
    }

    private static boolean extendsTargetRepresenter(ClassOrInterfaceDeclaration declaration) {
        List<ClassOrInterfaceType> extendedTypes = declaration.getExtendedTypes();
        for (ClassOrInterfaceType type : extendedTypes) {
            if (TARGET_SUPER_TYPE.equals(type.getNameAsString()) ||
                TARGET_SUPER_TYPE_FQCN.equals(type.asString())) {
                return true;
            }
        }
        return false;
    }
}
