package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.SimpleName;
import com.github.javaparser.ast.type.ClassOrInterfaceType;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Main {

    private static final String OLD_POINT_DISTANCE_FUNCTION = "org.tinspin.index.PointDistanceFunction";
    private static final String NEW_POINT_DISTANCE = "org.tinspin.index.PointDistance";
    private static final String OLD_POINT_ENTRY_DIST = "org.tinspin.index.PointEntryDist";
    private static final String NEW_POINT_ENTRY_KNN = "org.tinspin.index.Index.PointEntryKnn";
    private static final String OLD_POINT_INDEX = "org.tinspin.index.PointIndex";
    private static final String NEW_POINT_MAP = "org.tinspin.index.PointMap";

    public static void main(String[] args) {
        Path root = Paths.get(args.length > 0 ? args[0] : ".");

        try (Stream<Path> paths = Files.walk(root)) {
            List<Path> javaFiles = paths.filter(p -> Files.isRegularFile(p) && p.toString().endsWith(".java"))
                    .sorted(Comparator.naturalOrder())
                    .collect(Collectors.toList());

            for (Path file : javaFiles) {
                transformFile(file);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to traverse source tree: " + root, e);
        }
    }

    private static void transformFile(Path file) throws IOException {
        CompilationUnit cu;
        try {
            cu = StaticJavaParser.parse(file);
        } catch (Exception e) {
            return;
        }

        replaceImports(cu);
        replaceTinSpinTypes(cu);
        replaceLegacyMethodNames(cu);
        simplifyLegacyKdTreeCreateCalls(cu);

        String rewritten = cu.toString()
                .replace("PointDistanceFunction", "PointDistance")
                .replace("PointEntryDist", "PointEntryKnn")
                .replace("PointIndex", "PointMap")
                .replace("query1NN", "query1nn");

        Files.write(file, rewritten.getBytes(StandardCharsets.UTF_8));
    }

    private static void replaceImports(CompilationUnit cu) {
        removeImport(cu, OLD_POINT_DISTANCE_FUNCTION);
        removeImport(cu, OLD_POINT_ENTRY_DIST);
        removeImport(cu, OLD_POINT_INDEX);

        ensureImport(cu, NEW_POINT_DISTANCE);
        ensureImport(cu, NEW_POINT_ENTRY_KNN);
        ensureImport(cu, NEW_POINT_MAP);
    }

    private static void replaceTinSpinTypes(CompilationUnit cu) {
        for (ClassOrInterfaceType type : cu.findAll(ClassOrInterfaceType.class)) {
            String name = type.getNameAsString();
            if ("PointDistanceFunction".equals(name)) {
                type.setName(new SimpleName("PointDistance"));
            } else if ("PointEntryDist".equals(name)) {
                type.setName(new SimpleName("PointEntryKnn"));
            } else if ("PointIndex".equals(name)) {
                type.setName(new SimpleName("PointMap"));
            }
        }
    }

    private static void replaceLegacyMethodNames(CompilationUnit cu) {
        for (MethodCallExpr call : cu.findAll(MethodCallExpr.class)) {
            if ("query1NN".equals(call.getNameAsString())) {
                call.setName("query1nn");
            }
        }
    }

    private static void simplifyLegacyKdTreeCreateCalls(CompilationUnit cu) {
        for (MethodCallExpr call : cu.findAll(MethodCallExpr.class)) {
            if (!"create".equals(call.getNameAsString()) || call.getArguments().size() != 2) {
                continue;
            }

            if (!isSimpleNameScope(call, "KDTree")) {
                continue;
            }

            Expression second = call.getArgument(1);
            if (second.isLambdaExpr() || second.isMethodReferenceExpr() || second.isObjectCreationExpr()) {
                call.remove(second);
            }
        }
    }

    private static boolean isSimpleNameScope(MethodCallExpr call, String name) {
        return call.getScope().isPresent() && call.getScope().get().isNameExpr()
                && name.equals(call.getScope().get().asNameExpr().getNameAsString());
    }

    private static void removeImport(CompilationUnit cu, String importName) {
        cu.getImports().removeIf(i -> i.getNameAsString().equals(importName));
    }

    private static void ensureImport(CompilationUnit cu, String importName) {
        for (ImportDeclaration existing : cu.getImports()) {
            if (existing.getNameAsString().equals(importName)) {
                return;
            }
        }
        cu.addImport(importName);
    }
}
