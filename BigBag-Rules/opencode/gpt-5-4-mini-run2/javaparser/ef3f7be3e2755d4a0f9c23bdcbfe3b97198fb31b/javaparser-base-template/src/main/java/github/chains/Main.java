package github.chains;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;

public class Main {
    public static void main(String[] args) {
        if (args.length == 0) {
            throw new IllegalArgumentException("Expected a source directory path");
        }

        Path root = Paths.get(args[0]);
        try {
            Files.walk(root)
                .filter(p -> p.toString().endsWith(".java"))
                .sorted(Comparator.naturalOrder())
                .forEach(Main::transformFile);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void transformFile(Path path) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(path, StandardCharsets.UTF_8);

            boolean changed = false;

            changed |= replaceImports(cu);
            changed |= replaceTypes(cu);
            changed |= replaceMethodCalls(cu);
            changed |= fixKdtreeCreateCalls(cu);

            if (changed) {
                Files.writeString(path, cu.toString(), StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to transform " + path, e);
        }
    }

    private static boolean replaceImports(CompilationUnit cu) {
        boolean changed = false;
        changed |= replaceImport(cu, "org.tinspin.index.PointDistanceFunction", "org.tinspin.index.PointDistance");
        changed |= replaceImport(cu, "org.tinspin.index.PointEntryDist", "org.tinspin.index.Index.PointEntryKnn");
        changed |= replaceImport(cu, "org.tinspin.index.PointIndex", "org.tinspin.index.kdtree.KDTree");
        return changed;
    }

    private static boolean replaceImport(CompilationUnit cu, String oldName, String newName) {
        boolean removed = cu.getImports().removeIf(i -> !i.isAsterisk() && i.getNameAsString().equals(oldName));
        if (removed && cu.getImports().stream().noneMatch(i -> !i.isAsterisk() && i.getNameAsString().equals(newName))) {
            cu.addImport(newName);
        }
        return removed;
    }

    private static boolean replaceTypes(CompilationUnit cu) {
        boolean changed = false;
        for (ClassOrInterfaceType type : cu.findAll(ClassOrInterfaceType.class)) {
            String name = type.getNameAsString();
            if ("PointDistanceFunction".equals(name)) {
                type.setName("PointDistance");
                changed = true;
            } else if ("PointEntryDist".equals(name)) {
                type.setName("PointEntryKnn");
                changed = true;
            } else if ("PointIndex".equals(name)) {
                type.setName("KDTree");
                changed = true;
            }
        }
        return changed;
    }

    private static boolean replaceMethodCalls(CompilationUnit cu) {
        boolean changed = false;
        for (MethodCallExpr call : cu.findAll(MethodCallExpr.class)) {
            if ("query1NN".equals(call.getNameAsString())) {
                call.setName("query1nn");
                changed = true;
            }
        }
        return changed;
    }

    private static boolean fixKdtreeCreateCalls(CompilationUnit cu) {
        boolean changed = false;
        for (MethodCallExpr call : cu.findAll(MethodCallExpr.class)) {
            if (!"create".equals(call.getNameAsString())) {
                continue;
            }
            if (call.getScope().isPresent() && scopeLooksLikeKdtree(call.getScope().get())) {
                if (call.getArguments().size() >= 2 && (call.getArgument(1).isLambdaExpr() || call.getArgument(1).isMethodReferenceExpr())) {
                    call.getArguments().remove(1);
                    changed = true;
                }
            }
        }

        for (VariableDeclarator variable : cu.findAll(VariableDeclarator.class)) {
            if (variable.getType() instanceof ClassOrInterfaceType && "PointIndex".equals(((ClassOrInterfaceType) variable.getType()).getNameAsString())) {
                ((ClassOrInterfaceType) variable.getType()).setName("KDTree");
                changed = true;
            }
        }

        return changed;
    }

    private static boolean scopeLooksLikeKdtree(com.github.javaparser.ast.expr.Expression scope) {
        String text = scope.toString();
        return text.endsWith("KDTree") || text.endsWith("org.tinspin.index.kdtree.KDTree");
    }
}
