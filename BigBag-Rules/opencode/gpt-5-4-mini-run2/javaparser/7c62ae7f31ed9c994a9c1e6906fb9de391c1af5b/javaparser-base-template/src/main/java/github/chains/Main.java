package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: Main <source-root>");
            System.exit(1);
        }

        Path sourceRoot = Paths.get(args[0]).toAbsolutePath().normalize();

        try {
            Files.walk(sourceRoot)
                    .filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                    .sorted(Comparator.naturalOrder())
                    .forEach(Main::transformFile);
        } catch (IOException e) {
            throw new RuntimeException("Failed to transform sources under " + sourceRoot, e);
        }
    }

    private static void transformFile(Path path) {
        try {
            String original = Files.readString(path, StandardCharsets.UTF_8);
            CompilationUnit cu = StaticJavaParser.parse(original);

            boolean changed = false;

            for (ObjectCreationExpr creation : cu.findAll(ObjectCreationExpr.class)) {
                if (isOldServiceRegistryBuilderType(creation.getType())) {
                    creation.setType("StandardServiceRegistryBuilder");
                    changed = true;
                }
            }

            for (ClassOrInterfaceType type : cu.findAll(ClassOrInterfaceType.class)) {
                if (isOldServiceRegistryBuilderType(type)) {
                    type.setName("StandardServiceRegistryBuilder");
                    changed = true;
                }
            }

            for (MethodCallExpr call : cu.findAll(MethodCallExpr.class)) {
                if ("buildServiceRegistry".equals(call.getNameAsString())) {
                    call.setName("build");
                    changed = true;
                }
            }

            if (changed) {
                ensureImport(cu, "org.hibernate.boot.registry.StandardServiceRegistryBuilder");
                removeImport(cu, "org.hibernate.service.ServiceRegistryBuilder");
                Files.writeString(path, cu.toString(), StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to transform " + path, e);
        }
    }

    private static boolean isOldServiceRegistryBuilderType(ClassOrInterfaceType type) {
        String name = type.getNameAsString();
        return "ServiceRegistryBuilder".equals(name) || name.endsWith(".ServiceRegistryBuilder");
    }

    private static void ensureImport(CompilationUnit cu, String importName) {
        if (cu.getImports().stream().noneMatch(i -> !i.isAsterisk() && i.getNameAsString().equals(importName))) {
            cu.addImport(importName);
        }
    }

    private static void removeImport(CompilationUnit cu, String importName) {
        cu.getImports().removeIf(i -> !i.isAsterisk() && i.getNameAsString().equals(importName));
    }
}
