package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;

public class Main {

    private static final String OLD_INSPECTOR_FQCN = "org.yaml.snakeyaml.inspector.TrustedTagInspector";
    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            throw new IllegalArgumentException("Usage: Main <source-root>");
        }

        Path sourceRoot = Paths.get(args[0]);
        if (!Files.isDirectory(sourceRoot)) {
            throw new IllegalArgumentException("Not a directory: " + sourceRoot);
        }

        try (var paths = Files.walk(sourceRoot)) {
            paths.filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                    .sorted(Comparator.naturalOrder())
                    .forEach(Main::transformFile);
        }
    }

    private static void transformFile(Path path) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(path);
            LexicalPreservingPrinter.setup(cu);

            boolean changed = false;
            changed |= replaceImports(cu);
            changed |= replaceTypeUsages(cu);
            changed |= replaceConstructorCalls(cu);

            if (changed) {
                Files.writeString(path, LexicalPreservingPrinter.print(cu), StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to transform " + path, e);
        }
    }

    private static boolean replaceImports(CompilationUnit cu) {
        boolean changed = false;
        boolean importedOldInspector = cu.getImports().stream().anyMatch(importDecl -> importDecl.getNameAsString().equals(OLD_INSPECTOR_FQCN));

        if (importedOldInspector) {
            cu.getImports().removeIf(importDecl -> importDecl.getNameAsString().equals(OLD_INSPECTOR_FQCN));
            changed = true;
        }

        return changed;
    }

    private static boolean replaceTypeUsages(CompilationUnit cu) {
        boolean changed = false;
        for (ClassOrInterfaceType type : cu.findAll(ClassOrInterfaceType.class)) {
            if (isOldInspectorType(type)) {
                type.setName("TagInspector");
                type.removeScope();
                changed = true;
            }
        }
        return changed;
    }

    private static boolean replaceConstructorCalls(CompilationUnit cu) {
        boolean changed = false;
        for (ObjectCreationExpr expr : cu.findAll(ObjectCreationExpr.class)) {
            if (isOldInspectorCreation(expr) || isNewInspectorCreation(expr)) {
                expr.replace(buildAlwaysAllowedInspectorLambda());
                changed = true;
            }
        }
        return changed;
    }

    private static boolean containsOldTypeUsage(CompilationUnit cu) {
        return cu.findAll(ClassOrInterfaceType.class).stream().anyMatch(Main::isOldInspectorType);
    }

    private static boolean isOldInspectorType(ClassOrInterfaceType type) {
        return type.getNameAsString().equals("TrustedTagInspector") || type.asString().equals(OLD_INSPECTOR_FQCN);
    }

    private static boolean isOldInspectorCreation(ObjectCreationExpr expr) {
        return expr.getType().getNameAsString().equals("TrustedTagInspector") || expr.getType().asString().equals(OLD_INSPECTOR_FQCN);
    }

    private static boolean isNewInspectorCreation(ObjectCreationExpr expr) {
        return expr.getType().getNameAsString().equals("TagInspector") || expr.getType().asString().equals("org.yaml.snakeyaml.inspector.TagInspector");
    }

    private static Expression buildAlwaysAllowedInspectorLambda() {
        return StaticJavaParser.parseExpression("tag -> true");
    }
}
