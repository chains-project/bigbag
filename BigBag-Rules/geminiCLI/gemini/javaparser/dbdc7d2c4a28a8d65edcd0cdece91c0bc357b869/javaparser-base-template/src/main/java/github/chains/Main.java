package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.stmt.ExpressionStmt;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

public class Main {
    public static void main(String[] args) throws IOException {
        File file = new File("/workspace/ci-sauce/src/main/java/com/saucelabs/ci/sauceconnect/SauceConnectFourManager.java");
        CompilationUnit cu = StaticJavaParser.parse(file);

        cu.findAll(MethodCallExpr.class).stream()
                .filter(mce -> mce.getNameAsString().equals("enableLogging"))
                .filter(mce -> mce.getScope().isPresent() && mce.getScope().get().toString().equals("unArchiver"))
                .forEach(mce -> {
                    // Find the statement that contains this expression and remove it
                    if (mce.getParentNode().isPresent() && mce.getParentNode().get() instanceof ExpressionStmt) {
                        mce.getParentNode().get().remove();
                        System.out.println("Removed enableLogging call.");
                    }
                });

        try (FileWriter fileWriter = new FileWriter(file)) {
            fileWriter.write(cu.toString());
        }
    }
}