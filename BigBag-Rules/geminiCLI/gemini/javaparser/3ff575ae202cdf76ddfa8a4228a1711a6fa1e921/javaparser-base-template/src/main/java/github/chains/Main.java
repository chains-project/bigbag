package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.stmt.ExpressionStmt;

import java.io.File;
import java.io.FileWriter;

public class Main {
    public static void main(String[] args) throws Exception {
        File file = new File("/workspace/plexus-archiver/src/test/java/org/codehaus/plexus/archiver/DuplicateFilesTest.java");
        CompilationUnit cu = StaticJavaParser.parse(file);

        cu.findAll(MethodCallExpr.class).forEach(m -> {
            if (m.getNameAsString().equals("setThreshold")) {
                if (m.getScope().isPresent() && m.getScope().get() instanceof MethodCallExpr) {
                    MethodCallExpr scope = (MethodCallExpr) m.getScope().get();
                    if (scope.getNameAsString().equals("getLoggerManager")) {
                        // find the statement and remove it
                        m.findAncestor(ExpressionStmt.class).ifPresent(stmt -> stmt.remove());
                    }
                }
            }
        });

        try (FileWriter writer = new FileWriter(file)) {
            writer.write(cu.toString());
        }
    }
}
