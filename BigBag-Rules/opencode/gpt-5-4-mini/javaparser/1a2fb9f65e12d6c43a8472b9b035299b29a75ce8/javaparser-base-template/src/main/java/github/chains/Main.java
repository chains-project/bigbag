package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Main {
    private static final String OLD_PACKAGE = "javax.validation";
    private static final String NEW_PACKAGE = "jakarta.validation";

    public static void main(String[] args) {
        if (args.length != 1) {
            throw new IllegalArgumentException("Usage: Main <source-root>");
        }

        Path sourceRoot = Paths.get(args[0]);
        if (!Files.isDirectory(sourceRoot)) {
            throw new IllegalArgumentException("Not a directory: " + sourceRoot);
        }

        try (Stream<Path> paths = Files.walk(sourceRoot)) {
            List<Path> javaFiles = paths
                .filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                .collect(Collectors.toList());

            for (Path file : javaFiles) {
                transform(file);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to walk source tree", e);
        }
    }

    private static void transform(Path file) {
        try {
            CompilationUnit compilationUnit = StaticJavaParser.parse(file, StandardCharsets.UTF_8);
            LexicalPreservingPrinter.setup(compilationUnit);

            boolean changed = false;

            for (ImportDeclaration importDeclaration : compilationUnit.findAll(ImportDeclaration.class)) {
                String importedName = importDeclaration.getNameAsString();
                if (isValidationNamespace(importedName)) {
                    importDeclaration.setName(rewriteQualifiedName(importedName));
                    changed = true;
                }
            }

            for (ClassOrInterfaceType type : compilationUnit.findAll(ClassOrInterfaceType.class)) {
                String typeName = type.asString();
                if (isValidationNamespace(typeName)) {
                    type.replace(StaticJavaParser.parseClassOrInterfaceType(rewriteQualifiedName(typeName)));
                    changed = true;
                }
            }

            for (AnnotationExpr annotationExpr : compilationUnit.findAll(AnnotationExpr.class)) {
                String annotationName = annotationExpr.getNameAsString();
                if (isValidationNamespace(annotationName)) {
                    annotationExpr.setName(rewriteQualifiedName(annotationName));
                    changed = true;
                }
            }

            for (FieldAccessExpr fieldAccessExpr : compilationUnit.findAll(FieldAccessExpr.class)) {
                String fieldAccess = fieldAccessExpr.toString();
                if (isValidationNamespace(fieldAccess)) {
                    fieldAccessExpr.replace(StaticJavaParser.parseExpression(rewriteQualifiedName(fieldAccess)));
                    changed = true;
                }
            }

            for (MethodCallExpr methodCallExpr : compilationUnit.findAll(MethodCallExpr.class)) {
                if (!"getValidator".equals(methodCallExpr.getNameAsString())) {
                    continue;
                }

                if (methodCallExpr.getScope().isPresent()) {
                    String scope = methodCallExpr.getScope().get().toString();
                    if (scope.endsWith(".validatorFactory")) {
                        methodCallExpr.replace(StaticJavaParser.parseExpression(
                            "jakarta.validation.Validation.buildDefaultValidatorFactory().getValidator()"
                        ));
                        changed = true;
                    }
                }
            }

            if (changed) {
                Files.writeString(file, LexicalPreservingPrinter.print(compilationUnit), StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to transform " + file, e);
        }
    }

    private static boolean isValidationNamespace(String text) {
        return text.equals(OLD_PACKAGE) || text.startsWith(OLD_PACKAGE + ".");
    }

    private static String rewriteQualifiedName(String text) {
        return NEW_PACKAGE + text.substring(OLD_PACKAGE.length());
    }
}
