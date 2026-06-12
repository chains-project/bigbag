package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.body.FieldDeclaration;

import java.io.File;
import java.io.PrintWriter;

public class Main {
    public static void main(String[] args) throws Exception {
        processMeshing();
        processCirclePacking();
        processPointSet();
    }

    private static void processMeshing() throws Exception {
        File file = new File("/workspace/PGS/src/main/java/micycle/pgs/PGS_Meshing.java");
        CompilationUnit cu = StaticJavaParser.parse(file);

        cu.getImports().removeIf(i -> i.getNameAsString().equals("org.tinspin.index.PointIndex"));
        cu.addImport("org.tinspin.index.PointMap");

        cu.findAll(VariableDeclarator.class).forEach(vd -> {
            if (vd.getType().asString().startsWith("PointIndex<")) {
                vd.setType(vd.getType().asString().replace("PointIndex", "PointMap"));
                if (vd.getInitializer().isPresent() && vd.getInitializer().get().isMethodCallExpr()) {
                    MethodCallExpr call = vd.getInitializer().get().asMethodCallExpr();
                    if (call.getNameAsString().equals("create") && call.getScope().isPresent() && call.getScope().get().toString().equals("KDTree")) {
                        // Remove the distance function parameter (the second argument)
                        if (call.getArguments().size() > 1) {
                            call.getArguments().remove(1);
                        }
                    }
                }
            }
        });

        cu.findAll(MethodCallExpr.class).forEach(call -> {
            if (call.getNameAsString().equals("query1NN")) {
                call.setName("query1nn");
            }
        });

        try (PrintWriter out = new PrintWriter(file)) {
            out.println(cu.toString());
        }
    }

    private static void processCirclePacking() throws Exception {
        File file = new File("/workspace/PGS/src/main/java/micycle/pgs/PGS_CirclePacking.java");
        CompilationUnit cu = StaticJavaParser.parse(file);

        cu.getImports().removeIf(i -> i.getNameAsString().equals("org.tinspin.index.PointDistanceFunction"));
        cu.getImports().removeIf(i -> i.getNameAsString().equals("org.tinspin.index.PointEntryDist"));
        cu.addImport("org.tinspin.index.PointDistance");
        cu.addImport("org.tinspin.index.Index");

        cu.findAll(VariableDeclarator.class).forEach(vd -> {
            if (vd.getType().asString().startsWith("PointEntryDist<")) {
                vd.setType(vd.getType().asString().replace("PointEntryDist", "Index.PointEntryKnn"));
            }
        });

        cu.findAll(FieldDeclaration.class).forEach(fd -> {
            if (fd.getElementType().asString().equals("PointDistanceFunction")) {
                fd.getVariables().forEach(v -> v.setType("PointDistance"));
            }
        });

        cu.findAll(MethodCallExpr.class).forEach(call -> {
            if (call.getNameAsString().equals("query1NN")) {
                call.setName("query1nn");
            }
        });

        try (PrintWriter out = new PrintWriter(file)) {
            out.println(cu.toString());
        }
    }

    private static void processPointSet() throws Exception {
        File file = new File("/workspace/PGS/src/main/java/micycle/pgs/PGS_PointSet.java");
        CompilationUnit cu = StaticJavaParser.parse(file);

        cu.findAll(MethodCallExpr.class).forEach(call -> {
            if (call.getNameAsString().equals("query1NN")) {
                call.setName("query1nn");
            }
        });

        try (PrintWriter out = new PrintWriter(file)) {
            out.println(cu.toString());
        }
    }
}
