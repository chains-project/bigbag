package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.ImportDeclaration;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

public class Main {
    public static void main(String[] args) {
        if (args.length != 1) {
            throw new IllegalArgumentException("Expected a single source root path argument");
        }

        Path sourceRoot = Paths.get(args[0]);
        if (!Files.isDirectory(sourceRoot)) {
            throw new IllegalArgumentException("Source root is not a directory: " + sourceRoot);
        }

        try {
            List<Path> javaFiles;
            try (var paths = Files.walk(sourceRoot)) {
                javaFiles = paths.filter(path -> path.toString().endsWith(".java"))
                        .collect(Collectors.toList());
            }

            for (Path javaFile : javaFiles) {
                CompilationUnit compilationUnit = StaticJavaParser.parse(javaFile);
                if (transform(compilationUnit)) {
                    Files.writeString(javaFile, compilationUnit.toString(), StandardCharsets.UTF_8);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to process source root: " + sourceRoot, e);
        }
    }

    private static boolean transform(CompilationUnit compilationUnit) {
        boolean modified = false;
        for (ObjectCreationExpr objectCreationExpr : compilationUnit.findAll(ObjectCreationExpr.class)) {
            if (isSnakeYamlConstructorCreation(compilationUnit, objectCreationExpr)
                    && objectCreationExpr.getArguments().size() == 1) {
                objectCreationExpr.addArgument(StaticJavaParser.parseExpression("new org.yaml.snakeyaml.LoaderOptions()"));
                modified = true;
            }
        }
        return modified;
    }

    private static boolean isSnakeYamlConstructorCreation(CompilationUnit compilationUnit, ObjectCreationExpr objectCreationExpr) {
        String typeName = objectCreationExpr.getType().asString();
        if ("org.yaml.snakeyaml.constructor.Constructor".equals(typeName)) {
            return true;
        }
        if (!"Constructor".equals(typeName)) {
            return false;
        }

        for (ImportDeclaration importDeclaration : compilationUnit.getImports()) {
            if (importDeclaration.isAsterisk()) {
                if ("org.yaml.snakeyaml.constructor".equals(importDeclaration.getNameAsString())) {
                    return true;
                }
            } else if ("org.yaml.snakeyaml.constructor.Constructor".equals(importDeclaration.getNameAsString())) {
                return true;
            }
        }
        return false;
    }
}
