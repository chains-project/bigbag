package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class Main {
    private static final String REMOVED_TYPE = "HttpSessionContext";
    private static final String REMOVED_FQCN = "jakarta.servlet.http.HttpSessionContext";
    private static final String LEGACY_FQCN = "javax.servlet.http.HttpSessionContext";

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            throw new IllegalArgumentException("Expected a single source root argument");
        }

        Path root = Paths.get(args[0]);
        if (!Files.isDirectory(root)) {
            throw new IllegalArgumentException("Source root does not exist or is not a directory: " + root);
        }

        List<Path> javaFiles = new ArrayList<>();
        try (var stream = Files.walk(root)) {
            stream.filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                .forEach(javaFiles::add);
        }

        for (Path file : javaFiles) {
            rewrite(file);
        }
    }

    private static void rewrite(Path file) throws IOException {
        CompilationUnit cu = StaticJavaParser.parse(file);
        boolean changed = false;

        for (ImportDeclaration importDeclaration : new ArrayList<>(cu.getImports())) {
            if (isRemovedImport(importDeclaration)) {
                importDeclaration.remove();
                changed = true;
            }
        }

        for (MethodDeclaration methodDeclaration : new ArrayList<>(cu.findAll(MethodDeclaration.class))) {
            if (isRemovedMethod(methodDeclaration)) {
                methodDeclaration.getParentNode().ifPresent(parent -> parent.remove(methodDeclaration));
                changed = true;
            }
        }

        if (changed) {
            Files.writeString(file, cu.toString(), StandardCharsets.UTF_8);
        }
    }

    private static boolean isRemovedImport(ImportDeclaration importDeclaration) {
        String name = importDeclaration.getNameAsString();
        return REMOVED_FQCN.equals(name) || LEGACY_FQCN.equals(name);
    }

    private static boolean isRemovedMethod(MethodDeclaration methodDeclaration) {
        String name = methodDeclaration.getNameAsString();
        int arity = methodDeclaration.getParameters().size();
        return ("getSessionContext".equals(name) && arity == 0 && isRemovedType(methodDeclaration.getTypeAsString()))
            || ("getValue".equals(name) && arity == 1)
            || ("getValueNames".equals(name) && arity == 0)
            || ("putValue".equals(name) && arity == 2)
            || ("removeValue".equals(name) && arity == 1);
    }

    private static boolean isRemovedType(String typeName) {
        return REMOVED_TYPE.equals(typeName)
            || REMOVED_FQCN.equals(typeName)
            || LEGACY_FQCN.equals(typeName)
            || typeName.endsWith("." + REMOVED_TYPE);
    }
}
