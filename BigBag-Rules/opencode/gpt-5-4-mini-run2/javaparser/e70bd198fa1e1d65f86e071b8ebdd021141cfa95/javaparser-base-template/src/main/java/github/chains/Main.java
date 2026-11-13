package github.chains;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;

public class Main {

    private static final String OLD_POINT_INDEX = "org.tinspin.index.PointIndex";
    private static final String NEW_POINT_MAP = "org.tinspin.index.PointMap";
    private static final String OLD_POINT_DISTANCE_FUNCTION = "org.tinspin.index.PointDistanceFunction";
    private static final String NEW_POINT_DISTANCE = "org.tinspin.index.PointDistance";
    private static final String OLD_POINT_ENTRY_DIST = "org.tinspin.index.PointEntryDist";
    private static final String NEW_POINT_ENTRY_KNN = "org.tinspin.index.Index.PointEntryKnn";

    public static void main(String[] args) throws IOException {
        Path root = args.length > 0 ? Paths.get(args[0]) : Paths.get(".");
        root = root.toAbsolutePath().normalize();

        List<Path> javaFiles = Files.walk(root)
                .filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                .collect(Collectors.toList());

        int changedFiles = 0;
        for (Path file : javaFiles) {
            String original = Files.readString(file, StandardCharsets.UTF_8);
            Optional<String> updated = transform(original);
            if (updated.isPresent() && !updated.get().equals(original)) {
                Files.writeString(file, updated.get(), StandardCharsets.UTF_8);
                changedFiles++;
            }
        }

        System.out.println("Updated " + changedFiles + " file(s).");
    }

    private static Optional<String> transform(String source) {
        CompilationUnit cu = StaticJavaParser.parse(source);
        LexicalPreservingPrinter.setup(cu);

        boolean changed = false;
        changed |= replaceTypeNames(cu);
        changed |= replaceTinspinImports(cu);
        changed |= replaceMethodCalls(cu);
        changed |= replaceLegacyOverrides(cu);

        if (!changed) {
            return Optional.empty();
        }
        return Optional.of(LexicalPreservingPrinter.print(cu));
    }

    private static boolean replaceTinspinImports(CompilationUnit cu) {
        boolean changed = false;
        changed |= removeImport(cu, OLD_POINT_INDEX);
        changed |= removeImport(cu, OLD_POINT_DISTANCE_FUNCTION);
        changed |= removeImport(cu, OLD_POINT_ENTRY_DIST);

        changed |= addImport(cu, NEW_POINT_MAP);
        changed |= addImport(cu, NEW_POINT_DISTANCE);
        changed |= addImport(cu, NEW_POINT_ENTRY_KNN);

        return changed;
    }

    private static boolean removeImport(CompilationUnit cu, String name) {
        return cu.getImports().removeIf(importDecl -> importDecl.getNameAsString().equals(name));
    }

    private static boolean addImport(CompilationUnit cu, String name) {
        if (cu.getImports().stream().anyMatch(importDecl -> importDecl.getNameAsString().equals(name))) {
            return false;
        }
        cu.addImport(name);
        return true;
    }

    private static boolean replaceTypeNames(CompilationUnit cu) {
        boolean changed = false;
        for (ClassOrInterfaceType type : cu.findAll(ClassOrInterfaceType.class)) {
            String simpleName = type.getName().asString();
            if (OLD_POINT_INDEX.endsWith("." + simpleName)) {
                type.setName("PointMap");
                changed = true;
            } else if (OLD_POINT_DISTANCE_FUNCTION.endsWith("." + simpleName)) {
                type.setName("PointDistance");
                changed = true;
            } else if (OLD_POINT_ENTRY_DIST.endsWith("." + simpleName)) {
                type.setName("PointEntryKnn");
                changed = true;
            } else if ("PointIndex".equals(simpleName)) {
                type.setName("PointMap");
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

            if (isLegacyKdTreeCreate(call)) {
                call.getArguments().remove(1);
                changed = true;
            }
        }
        return changed;
    }

    private static boolean isLegacyKdTreeCreate(MethodCallExpr call) {
        if (!"create".equals(call.getNameAsString()) || call.getArguments().size() != 2) {
            return false;
        }
        if (call.getScope().isEmpty()) {
            return false;
        }
        String scope = call.getScope().get().toString();
        return scope.endsWith("KDTree");
    }

    private static boolean replaceLegacyOverrides(CompilationUnit cu) {
        boolean changed = false;
        for (MethodDeclaration method : cu.findAll(MethodDeclaration.class)) {
            if ("distance".equals(method.getNameAsString()) && method.getParameters().size() == 2
                    && method.getParameters().stream().allMatch(p -> "double[]".equals(p.getType().toString()))) {
                method.setName("dist");
                changed = true;
            }

            if ("PointIndex".equals(method.getType().asString())) {
                method.getType().asClassOrInterfaceType().setName("PointMap");
                changed = true;
            }
        }
        return changed;
    }
}
