package github.chains;

import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;
import java.util.stream.Stream;

public class Main {
    private static final String OLD_PACKAGE = "javax.validation";
    private static final String NEW_PACKAGE = "jakarta.validation";

    public static void main(String[] args) {
        Path sourceRoot = args.length > 0 ? Paths.get(args[0]) : Paths.get(".");
        sourceRoot = sourceRoot.toAbsolutePath().normalize();

        StaticJavaParser.setConfiguration(new ParserConfiguration()
                .setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_11));

        try (Stream<Path> paths = Files.walk(sourceRoot)) {
            paths.filter(Main::isJavaSourceFile)
                    .forEach(Main::rewriteFile);
        } catch (IOException e) {
            throw new RuntimeException("Failed to traverse source root: " + sourceRoot, e);
        }
    }

    private static boolean isJavaSourceFile(Path path) {
        return Files.isRegularFile(path)
                && path.toString().endsWith(".java")
                && !containsTargetDirectory(path);
    }

    private static boolean containsTargetDirectory(Path path) {
        for (Path element : path) {
            if (Objects.equals(element.toString(), "target")) {
                return true;
            }
        }
        return false;
    }

    private static void rewriteFile(Path file) {
        try {
            CompilationUnit compilationUnit = StaticJavaParser.parse(file);
            LexicalPreservingPrinter.setup(compilationUnit);

            boolean changed = false;
            changed |= rewriteImports(compilationUnit);
            changed |= rewriteQualifiedNames(compilationUnit);
            changed |= rewriteValidatorFactoryAccess(compilationUnit);

            if (changed) {
                Files.writeString(file, LexicalPreservingPrinter.print(compilationUnit), StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to rewrite " + file, e);
        }
    }

    private static boolean rewriteImports(CompilationUnit compilationUnit) {
        boolean changed = false;
        for (ImportDeclaration importDeclaration : compilationUnit.getImports()) {
            String importName = importDeclaration.getNameAsString();
            if (importName.startsWith(OLD_PACKAGE)) {
                importDeclaration.setName(StaticJavaParser.parseName(importName.replace(OLD_PACKAGE, NEW_PACKAGE)));
                changed = true;
            }
        }
        return changed;
    }

    private static boolean rewriteQualifiedNames(CompilationUnit compilationUnit) {
        boolean changed = false;

        for (Node node : compilationUnit.findAll(Node.class)) {
            if (node instanceof Type) {
                Type type = (Type) node;
                String typeText = type.toString();
                if (typeText.contains(OLD_PACKAGE)) {
                    type.replace(StaticJavaParser.parseType(typeText.replace(OLD_PACKAGE, NEW_PACKAGE)));
                    changed = true;
                }
            } else if (node instanceof AnnotationExpr) {
                AnnotationExpr annotationExpr = (AnnotationExpr) node;
                String annotationText = annotationExpr.toString();
                if (annotationText.contains(OLD_PACKAGE)) {
                    annotationExpr.replace(StaticJavaParser.parseAnnotation(annotationText.replace(OLD_PACKAGE, NEW_PACKAGE)));
                    changed = true;
                }
            }
        }

        return changed;
    }

    private static boolean rewriteValidatorFactoryAccess(CompilationUnit compilationUnit) {
        boolean changed = false;

        for (MethodCallExpr methodCallExpr : compilationUnit.findAll(MethodCallExpr.class)) {
            if (!"getValidator".equals(methodCallExpr.getNameAsString()) || methodCallExpr.getScope().isEmpty()) {
                continue;
            }

            Expression scope = methodCallExpr.getScope().get();
            if (scope instanceof FieldAccessExpr) {
                FieldAccessExpr fieldAccessExpr = (FieldAccessExpr) scope;
                if ("validatorFactory".equals(fieldAccessExpr.getNameAsString())) {
                    methodCallExpr.setScope(StaticJavaParser.parseExpression("jakarta.validation.Validation.buildDefaultValidatorFactory()"));
                    changed = true;
                }
            }
        }

        return changed;
    }
}
