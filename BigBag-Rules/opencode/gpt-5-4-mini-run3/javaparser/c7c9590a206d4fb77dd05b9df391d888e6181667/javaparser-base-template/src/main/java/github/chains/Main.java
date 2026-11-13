package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class Main {
    private static final String OLD_RENDERING_CONTEXT =
            "org.apache.maven.doxia.module.xhtml.decoration.render.RenderingContext";
    private static final String NEW_RENDERING_CONTEXT =
            "org.apache.maven.doxia.siterenderer.RenderingContext";

    public static void main(String[] args) throws IOException {
        Path sourceRoot = Paths.get(args.length > 0 ? args[0] : ".");

        try (var paths = Files.walk(sourceRoot)) {
            paths.filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                    .forEach(Main::rewriteFile);
        }
    }

    private static void rewriteFile(Path path) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(path, StandardCharsets.UTF_8);
            boolean changed = updateImports(cu) | updateTypeUsages(cu);

            if (changed) {
                Files.writeString(path, cu.toString(), StandardCharsets.UTF_8);
            }
        } catch (Exception ignored) {
            // Keep the transformation resilient across varied source trees.
        }
    }

    private static boolean updateImports(CompilationUnit cu) {
        boolean changed = false;
        for (ImportDeclaration importDeclaration : cu.getImports()) {
            if (OLD_RENDERING_CONTEXT.equals(importDeclaration.getNameAsString())) {
                importDeclaration.setName(StaticJavaParser.parseName(NEW_RENDERING_CONTEXT));
                changed = true;
            }
        }
        return changed;
    }

    private static boolean updateTypeUsages(CompilationUnit cu) {
        boolean oldImportPresent = cu.getImports().stream()
                .map(ImportDeclaration::getNameAsString)
                .anyMatch(OLD_RENDERING_CONTEXT::equals);

        boolean changed = false;
        for (ClassOrInterfaceType type : cu.findAll(ClassOrInterfaceType.class)) {
            String typeText = type.asString();
            if (OLD_RENDERING_CONTEXT.equals(typeText)
                    || (oldImportPresent && "RenderingContext".equals(type.getNameAsString()))) {
                type.replace(StaticJavaParser.parseClassOrInterfaceType(NEW_RENDERING_CONTEXT));
                changed = true;
            }
        }

        for (ObjectCreationExpr creationExpr : cu.findAll(ObjectCreationExpr.class)) {
            if (OLD_RENDERING_CONTEXT.equals(creationExpr.getType().asString())
                    || (oldImportPresent && "RenderingContext".equals(creationExpr.getType().getNameAsString()))) {
                creationExpr.setType(StaticJavaParser.parseClassOrInterfaceType(NEW_RENDERING_CONTEXT));
                changed = true;
            }
        }

        for (VariableDeclarator variableDeclarator : cu.findAll(VariableDeclarator.class)) {
            if (OLD_RENDERING_CONTEXT.equals(variableDeclarator.getType().asString())) {
                variableDeclarator.setType(StaticJavaParser.parseType(NEW_RENDERING_CONTEXT));
                changed = true;
            }
        }

        return changed;
    }
}
