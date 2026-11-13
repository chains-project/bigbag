package github.chains;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;
import java.util.stream.Stream;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;

public class Main {

    private static final String POINT_INDEX = "org.tinspin.index.PointIndex";
    private static final String POINT_INDEX_SIMPLE = "PointIndex";
    private static final String POINT_MAP = "org.tinspin.index.PointMap";
    private static final String POINT_MAP_SIMPLE = "PointMap";
    private static final String POINT_DISTANCE_FUNCTION = "org.tinspin.index.PointDistanceFunction";
    private static final String POINT_DISTANCE_FUNCTION_SIMPLE = "PointDistanceFunction";
    private static final String POINT_DISTANCE = "org.tinspin.index.PointDistance";
    private static final String POINT_DISTANCE_SIMPLE = "PointDistance";
    private static final String POINT_ENTRY_DIST = "org.tinspin.index.PointEntryDist";
    private static final String POINT_ENTRY_DIST_SIMPLE = "PointEntryDist";
    private static final String POINT_ENTRY_KNN = "org.tinspin.index.Index.PointEntryKnn";
    private static final String POINT_ENTRY_KNN_SIMPLE = "PointEntryKnn";

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            throw new IllegalArgumentException("Usage: Main <source-root>");
        }

        Path sourceRoot = Paths.get(args[0]);
        if (!Files.isDirectory(sourceRoot)) {
            throw new IllegalArgumentException("Not a directory: " + sourceRoot);
        }

        try (Stream<Path> paths = Files.walk(sourceRoot)) {
            paths.filter(path -> path.toString().endsWith(".java")).forEach(Main::transformFile);
        }
    }

    private static void transformFile(Path file) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(file);
            boolean changed = false;
            for (ClassOrInterfaceType t : cu.findAll(ClassOrInterfaceType.class)) {
                String name = t.getNameAsString();
                if (POINT_INDEX_SIMPLE.equals(name)) {
                    t.setName(POINT_MAP_SIMPLE);
                    changed = true;
                } else if (POINT_DISTANCE_FUNCTION_SIMPLE.equals(name)) {
                    t.setName(POINT_DISTANCE_SIMPLE);
                    changed = true;
                } else if (POINT_ENTRY_DIST_SIMPLE.equals(name)) {
                    t.setName(POINT_ENTRY_KNN_SIMPLE);
                    changed = true;
                }
            }

            for (MethodCallExpr n : cu.findAll(MethodCallExpr.class)) {
                if ("query1NN".equals(n.getNameAsString())) {
                    n.setName("query1nn");
                    changed = true;
                }
                if ("create".equals(n.getNameAsString()) && n.getScope().isPresent()
                        && "KDTree".equals(n.getScope().get().toString()) && n.getArguments().size() == 2) {
                    n.getArguments().remove(1);
                    changed = true;
                }
            }

            if (changed) {
                ensureImport(cu, POINT_MAP);
                ensureImport(cu, POINT_DISTANCE);
                ensureImport(cu, POINT_ENTRY_KNN);
                removeImport(cu, POINT_INDEX);
                removeImport(cu, POINT_DISTANCE_FUNCTION);
                removeImport(cu, POINT_ENTRY_DIST);

                Files.writeString(file, cu.toString());
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to transform " + file, e);
        }
    }

    private static void ensureImport(CompilationUnit cu, String fqcn) {
        if (cu.getImports().stream().noneMatch(i -> Objects.equals(i.getNameAsString(), fqcn))) {
            cu.addImport(fqcn);
        }
    }

    private static void removeImport(CompilationUnit cu, String fqcn) {
        cu.getImports().removeIf(i -> Objects.equals(i.getNameAsString(), fqcn));
    }

}
