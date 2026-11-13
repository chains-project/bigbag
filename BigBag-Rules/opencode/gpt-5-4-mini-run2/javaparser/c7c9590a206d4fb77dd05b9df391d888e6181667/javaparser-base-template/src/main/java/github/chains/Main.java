package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;

public class Main {
    private static final String OLD_FQCN = "org.apache.maven.doxia.module.xhtml.decoration.render.RenderingContext";
    private static final String NEW_FQCN = "org.apache.maven.doxia.siterenderer.RenderingContext";
    private static final String OLD_SIMPLE = "RenderingContext";
    private static final String NEW_SIMPLE = "RenderingContext";

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            throw new IllegalArgumentException("Expected source root path as the only argument");
        }

        Path sourceRoot = Paths.get(args[0]);
        Files.walk(sourceRoot)
            .filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
            .sorted(Comparator.naturalOrder())
            .forEach(Main::rewriteIfNeeded);
    }

    private static void rewriteIfNeeded(Path javaFile) {
        try {
            String source = Files.readString(javaFile, StandardCharsets.UTF_8);
            CompilationUnit cu = StaticJavaParser.parse(source);

            boolean changed = false;
            boolean hadOldImport = cu.getImports().stream()
                .anyMatch(importDeclaration -> importDeclaration.getNameAsString().equals(OLD_FQCN));

            changed |= cu.getImports().removeIf(importDeclaration -> importDeclaration.getNameAsString().equals(OLD_FQCN));

            if (hadOldImport || usesOldType(cu)) {
                if (cu.getImports().stream().noneMatch(importDeclaration -> importDeclaration.getNameAsString().equals(NEW_FQCN))) {
                    cu.addImport(NEW_FQCN);
                }
                changed |= replaceOldTypeUsages(cu);
            }

            if (changed) {
                Files.writeString(javaFile, cu.toString(), StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to transform " + javaFile, e);
        }
    }

    private static boolean usesOldType(CompilationUnit cu) {
        return cu.getImports().stream().anyMatch(importDeclaration -> importDeclaration.getNameAsString().equals(OLD_FQCN))
            || cu.findAll(ClassOrInterfaceType.class).stream().anyMatch(type -> type.asString().equals(OLD_SIMPLE))
            || cu.findAll(ObjectCreationExpr.class).stream().anyMatch(expr -> expr.getType().asString().equals(OLD_SIMPLE))
            || cu.findAll(Parameter.class).stream().anyMatch(parameter -> parameter.getType().asString().equals(OLD_SIMPLE));
    }

    private static boolean replaceOldTypeUsages(CompilationUnit cu) {
        final boolean[] changed = {false};

        cu.findAll(ClassOrInterfaceType.class).forEach(type -> {
            if (type.asString().equals(OLD_SIMPLE) || type.asString().equals(OLD_FQCN)) {
                type.setName(NEW_SIMPLE);
                changed[0] = true;
            }
        });

        cu.findAll(ObjectCreationExpr.class).forEach(expr -> {
            if (expr.getType().asString().equals(OLD_SIMPLE) || expr.getType().asString().equals(OLD_FQCN)) {
                expr.getType().setName(NEW_SIMPLE);
                changed[0] = true;
            }
        });

        cu.findAll(Parameter.class).forEach(parameter -> {
            if (parameter.getType().asString().equals(OLD_SIMPLE) || parameter.getType().asString().equals(OLD_FQCN)) {
                parameter.setType(NEW_FQCN);
                changed[0] = true;
            }
        });

        return changed[0];
    }
}
