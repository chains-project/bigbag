package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.SimpleName;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ParserConfiguration;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

public class Main {

    private static final String OLD_POINT_INDEX = "org.tinspin.index.PointIndex";
    private static final String NEW_POINT_MAP = "org.tinspin.index.PointMap";
    private static final String OLD_POINT_DISTANCE_FUNCTION = "org.tinspin.index.PointDistanceFunction";
    private static final String NEW_POINT_DISTANCE = "org.tinspin.index.PointDistance";
    private static final String OLD_POINT_ENTRY_DIST = "org.tinspin.index.PointEntryDist";
    private static final String NEW_POINT_ENTRY_KNN = "org.tinspin.index.Index.PointEntryKnn";

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException("Usage: Main <source-directory>");
        }

        Path root = Paths.get(args[0]).toAbsolutePath().normalize();
        if (!Files.isDirectory(root)) {
            throw new IllegalArgumentException("Not a directory: " + root);
        }

        StaticJavaParser.getParserConfiguration().setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_11);

        List<Path> sources = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(root)) {
            stream.filter(p -> Files.isRegularFile(p) && p.toString().endsWith(".java")).forEach(sources::add);
        }

        for (Path source : sources) {
            transformFile(source);
        }
    }

    private static void transformFile(Path source) throws IOException {
        CompilationUnit cu = StaticJavaParser.parse(source, StandardCharsets.UTF_8);

        boolean changed = false;
        changed |= rewriteImports(cu);
        changed |= rewriteTypes(cu);
        changed |= rewriteVariableTypes(cu);
        changed |= rewriteSimpleNames(cu);
        changed |= rewriteMethodCalls(cu);
        changed |= rewriteKDTreeCreate(cu);

        if (changed) {
            Files.writeString(source, cu.toString(), StandardCharsets.UTF_8);
        }
    }

    private static boolean rewriteImports(CompilationUnit cu) {
        boolean changed = false;
        changed |= replaceImport(cu, OLD_POINT_INDEX, NEW_POINT_MAP);
        changed |= replaceImport(cu, OLD_POINT_DISTANCE_FUNCTION, NEW_POINT_DISTANCE);
        changed |= replaceImport(cu, OLD_POINT_ENTRY_DIST, NEW_POINT_ENTRY_KNN);
        return changed;
    }

    private static boolean replaceImport(CompilationUnit cu, String oldName, String newName) {
        boolean changed = false;
        for (ImportDeclaration importDecl : cu.getImports()) {
            if (importDecl.getNameAsString().equals(oldName)) {
                importDecl.setName(newName);
                changed = true;
            }
        }
        return changed;
    }

    private static boolean rewriteTypes(CompilationUnit cu) {
        boolean changed = false;
        for (Type type : cu.findAll(Type.class)) {
            changed |= rewriteType(type);
        }
        return changed;
    }

    private static boolean rewriteVariableTypes(CompilationUnit cu) {
        boolean changed = false;
        for (VariableDeclarator vd : cu.findAll(VariableDeclarator.class)) {
            changed |= rewriteType(vd.getType());
        }
        return changed;
    }

    private static boolean rewriteType(Type type) {
        boolean changed = false;
        if (type.isClassOrInterfaceType()) {
            ClassOrInterfaceType cit = type.asClassOrInterfaceType();
            String name = cit.getNameAsString();
            if (name.equals("PointIndex")) {
                cit.setName("PointMap");
                changed = true;
            } else if (name.equals("PointDistanceFunction")) {
                cit.setName("PointDistance");
                changed = true;
            } else if (name.equals("PointEntryDist")) {
                cit.setName("PointEntryKnn");
                changed = true;
            }
        }
        return changed;
    }

    private static boolean rewriteSimpleNames(CompilationUnit cu) {
        boolean changed = false;
        for (SimpleName name : cu.findAll(SimpleName.class)) {
            String id = name.asString();
            if (id.equals("PointIndex") || id.equals("PointDistanceFunction") || id.equals("PointEntryDist")) {
                if (isInTypeOrImportContext(name)) {
                    name.setIdentifier(rewriteIdentifier(id));
                    changed = true;
                }
            }
        }
        return changed;
    }

    private static boolean isInTypeOrImportContext(SimpleName name) {
        Node current = name;
        while (current.getParentNode().isPresent()) {
            current = current.getParentNode().get();
            if (current instanceof ImportDeclaration) {
                return true;
            }
            if (current instanceof Type || current instanceof ObjectCreationExpr) {
                return true;
            }
        }
        return false;
    }

    private static String rewriteIdentifier(String id) {
        if (id.equals("PointIndex")) {
            return "PointMap";
        }
        if (id.equals("PointDistanceFunction")) {
            return "PointDistance";
        }
        if (id.equals("PointEntryDist")) {
            return "PointEntryKnn";
        }
        return id;
    }

    private static boolean rewriteMethodCalls(CompilationUnit cu) {
        boolean changed = false;
        for (MethodCallExpr call : cu.findAll(MethodCallExpr.class)) {
            if (call.getNameAsString().equals("query1NN")) {
                call.setName("query1nn");
                changed = true;
            }
        }
        return changed;
    }

    private static boolean rewriteKDTreeCreate(CompilationUnit cu) {
        boolean changed = false;
        for (MethodCallExpr call : cu.findAll(MethodCallExpr.class)) {
            if (!call.getNameAsString().equals("create") || call.getArguments().size() != 2) {
                continue;
            }

            if (call.getScope().isPresent() && call.getScope().get().toString().endsWith("KDTree")) {
                call.getArguments().remove(1);
                changed = true;
            }
        }
        return changed;
    }
}
