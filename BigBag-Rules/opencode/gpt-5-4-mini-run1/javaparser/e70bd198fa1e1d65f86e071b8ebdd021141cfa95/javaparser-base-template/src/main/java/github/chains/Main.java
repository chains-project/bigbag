package github.chains;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;

public class Main {

    private static final String OLD_POINT_DISTANCE_FUNCTION = "org.tinspin.index.PointDistanceFunction";
    private static final String NEW_POINT_DISTANCE = "org.tinspin.index.PointDistance";
    private static final String OLD_POINT_ENTRY_DIST = "org.tinspin.index.PointEntryDist";
    private static final String NEW_POINT_ENTRY_KNN = "org.tinspin.index.Index.PointEntryKnn";
    private static final String OLD_POINT_INDEX = "org.tinspin.index.PointIndex";
    private static final String NEW_KD_TREE = "org.tinspin.index.kdtree.KDTree";

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException("Usage: Main <source-directory>");
        }

        Path root = Paths.get(args[0]);
        if (!Files.isDirectory(root)) {
            throw new IllegalArgumentException("Not a directory: " + root);
        }

        try (Stream<Path> files = Files.walk(root)) {
            files.filter(path -> path.toString().endsWith(".java")).forEach(Main::rewriteFileUnchecked);
        }
    }

    private static void rewriteFileUnchecked(Path file) {
        try {
            rewriteFile(file);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void rewriteFile(Path file) throws IOException {
        CompilationUnit cu = StaticJavaParser.parse(file);
        LexicalPreservingPrinter.setup(cu);

        boolean changed = false;
        changed |= rewriteImports(cu);
        changed |= rewriteTypes(cu);
        changed |= rewriteMethodCalls(cu);

        if (changed) {
            Files.write(file, LexicalPreservingPrinter.print(cu).getBytes(StandardCharsets.UTF_8));
        }
    }

    private static boolean rewriteImports(CompilationUnit cu) {
        boolean changed = false;
        changed |= replaceImport(cu, OLD_POINT_DISTANCE_FUNCTION, NEW_POINT_DISTANCE);
        changed |= replaceImport(cu, OLD_POINT_ENTRY_DIST, NEW_POINT_ENTRY_KNN);
        changed |= replaceImport(cu, OLD_POINT_INDEX, NEW_KD_TREE);
        return changed;
    }

    private static boolean replaceImport(CompilationUnit cu, String oldImport, String newImport) {
        boolean changed = cu.getImports().removeIf(i -> i.getNameAsString().equals(oldImport));
        if (!cu.getImports().stream().anyMatch(i -> i.getNameAsString().equals(newImport))) {
            cu.addImport(newImport);
            changed = true;
        }
        return changed;
    }

    private static boolean rewriteTypes(CompilationUnit cu) {
        boolean changed = false;
        Set<ClassOrInterfaceType> types = new HashSet<>(cu.findAll(ClassOrInterfaceType.class));
        for (ClassOrInterfaceType type : types) {
            String name = type.getNameAsString();
            if (OLD_POINT_DISTANCE_FUNCTION.endsWith("." + name)) {
                type.setName("PointDistance");
                changed = true;
            } else if (OLD_POINT_ENTRY_DIST.endsWith("." + name)) {
                type.setName("PointEntryKnn");
                type.setScope(new ClassOrInterfaceType(null, "Index"));
                changed = true;
            }
        }

        // Only rewrite PointIndex where it is used as the type for a KDTree factory result.
        for (var variable : cu.findAll(com.github.javaparser.ast.body.VariableDeclarator.class)) {
            if (!variable.getType().isClassOrInterfaceType()) {
                continue;
            }
            ClassOrInterfaceType type = variable.getType().asClassOrInterfaceType();
            if (!type.getNameAsString().equals("PointIndex") || variable.getInitializer().isEmpty()) {
                continue;
            }

            Expression init = variable.getInitializer().get();
            if (init.isMethodCallExpr()) {
                MethodCallExpr call = init.asMethodCallExpr();
                if (call.getNameAsString().equals("create") && isKDTreeScope(call.getScope().orElse(null))) {
                    type.setName("KDTree");
                    changed = true;
                }
            }
        }

        return changed;
    }

    private static boolean rewriteMethodCalls(CompilationUnit cu) {
        boolean changed = false;
        for (MethodCallExpr call : cu.findAll(MethodCallExpr.class)) {
            if (call.getNameAsString().equals("query1NN")) {
                call.setName("query1nn");
                changed = true;
            }

            if (call.getNameAsString().equals("create") && isKDTreeScope(call.getScope().orElse(null)) && call.getArguments().size() == 2) {
                call.remove(call.getArguments().get(1));
                changed = true;
            }
        }
        return changed;
    }

    private static boolean isKDTreeScope(Expression scope) {
        if (scope == null) {
            return false;
        }
        String text = scope.toString();
        return text.equals("KDTree") || text.endsWith(".KDTree");
    }
}
