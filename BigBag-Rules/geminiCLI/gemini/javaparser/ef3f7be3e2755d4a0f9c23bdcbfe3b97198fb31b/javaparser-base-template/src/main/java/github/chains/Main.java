package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Paths;

public class Main {
    public static void main(String[] args) throws Exception {
        processMeshing();
        processCirclePacking();
        processPointSet();
    }

    private static void processMeshing() throws Exception {
        File file = new File("/workspace/PGS/src/main/java/micycle/pgs/PGS_Meshing.java");
        CompilationUnit cu = StaticJavaParser.parse(file);

        // Replace import
        for (ImportDeclaration id : cu.getImports()) {
            if (id.getNameAsString().equals("org.tinspin.index.PointIndex")) {
                id.setName("org.tinspin.index.PointMap");
            }
        }

        cu.accept(new VoidVisitorAdapter<Void>() {
            @Override
            public void visit(VariableDeclarator vd, Void arg) {
                super.visit(vd, arg);
                // change PointIndex to PointMap
                if (vd.getType().isClassOrInterfaceType() && vd.getType().asClassOrInterfaceType().getNameAsString().equals("PointIndex")) {
                    vd.getType().asClassOrInterfaceType().setName("PointMap");
                }
                
                if (vd.getNameAsString().equals("tree") && vd.getInitializer().isPresent()) {
                    if (vd.getInitializer().get().isMethodCallExpr()) {
                        MethodCallExpr mce = vd.getInitializer().get().asMethodCallExpr();
                        if (mce.getNameAsString().equals("create") && mce.getArguments().size() == 2) {
                            // Remove the second argument (the lambda)
                            mce.getArguments().removeLast();
                        }
                    }
                }
            }

            @Override
            public void visit(MethodCallExpr n, Void arg) {
                super.visit(n, arg);
                if (n.getNameAsString().equals("query1NN")) {
                    n.setName("query1nn");
                }
            }
        }, null);

        Files.write(Paths.get(file.getPath()), cu.toString().getBytes());
    }

    private static void processCirclePacking() throws Exception {
        File file = new File("/workspace/PGS/src/main/java/micycle/pgs/PGS_CirclePacking.java");
        CompilationUnit cu = StaticJavaParser.parse(file);

        for (ImportDeclaration id : cu.getImports()) {
            if (id.getNameAsString().equals("org.tinspin.index.PointDistanceFunction")) {
                id.setName("org.tinspin.index.PointDistance");
            } else if (id.getNameAsString().equals("org.tinspin.index.PointEntryDist")) {
                id.setName("org.tinspin.index.Index.PointEntryKnn");
            }
        }

        cu.accept(new VoidVisitorAdapter<Void>() {
            @Override
            public void visit(VariableDeclarator vd, Void arg) {
                super.visit(vd, arg);
                if (vd.getType().isClassOrInterfaceType() && vd.getType().asClassOrInterfaceType().getNameAsString().equals("PointEntryDist")) {
                    vd.getType().asClassOrInterfaceType().setName("PointEntryKnn");
                }
            }
            
            @Override
            public void visit(FieldDeclaration fd, Void arg) {
                super.visit(fd, arg);
                if (fd.getVariables().size() > 0 && fd.getVariable(0).getNameAsString().equals("circleDistanceMetric")) {
                    if (fd.getElementType().isClassOrInterfaceType()) {
                        fd.getElementType().asClassOrInterfaceType().setName("PointDistance");
                    }
                }
            }

            @Override
            public void visit(MethodCallExpr n, Void arg) {
                super.visit(n, arg);
                if (n.getNameAsString().equals("query1NN")) {
                    n.setName("query1nn");
                }
            }
        }, null);

        Files.write(Paths.get(file.getPath()), cu.toString().getBytes());
    }

    private static void processPointSet() throws Exception {
        File file = new File("/workspace/PGS/src/main/java/micycle/pgs/PGS_PointSet.java");
        CompilationUnit cu = StaticJavaParser.parse(file);

        cu.accept(new VoidVisitorAdapter<Void>() {
            @Override
            public void visit(MethodCallExpr n, Void arg) {
                super.visit(n, arg);
                if (n.getNameAsString().equals("query1NN")) {
                    n.setName("query1nn");
                }
            }
        }, null);

        Files.write(Paths.get(file.getPath()), cu.toString().getBytes());
    }
}