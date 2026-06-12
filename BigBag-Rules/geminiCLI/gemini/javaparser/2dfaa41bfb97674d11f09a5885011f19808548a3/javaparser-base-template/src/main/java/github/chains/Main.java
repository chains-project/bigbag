package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;

public class Main {
    public static void main(String[] args) throws Exception {
        File file = new File("/workspace/bval/bval-jsr/src/test/java/org/apache/bval/jsr/DefaultMessageInterpolatorTest.java");
        CompilationUnit cu = StaticJavaParser.parse(file);

        cu.accept(new VoidVisitorAdapter<Void>() {
            @Override
            public void visit(MethodCallExpr n, Void arg) {
                super.visit(n, arg);
                if ("getArgumentAt".equals(n.getNameAsString())) {
                    n.setName("getArgument");
                }
            }
        }, null);

        Files.write(Paths.get(file.getAbsolutePath()), cu.toString().getBytes());
    }
}
