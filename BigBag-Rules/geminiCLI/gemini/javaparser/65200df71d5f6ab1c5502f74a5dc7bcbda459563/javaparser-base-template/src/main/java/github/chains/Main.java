package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

public class Main {
    public static void main(String[] args) throws IOException {
        String filePath = "/workspace/sorald/sorald/src/main/java/sorald/sonar/SonarLintEngine.java";
        File file = new File(filePath);

        CompilationUnit cu = StaticJavaParser.parse(file);

        cu.accept(new VoidVisitorAdapter<Void>() {
            @Override
            public void visit(MethodCallExpr n, Void arg) {
                if (n.getNameAsString().equals("addEnabledLanguages")) {
                    if (n.getScope().isPresent()) {
                        n.replace(n.getScope().get());
                    }
                }
                super.visit(n, arg);
            }
        }, null);

        Files.write(Paths.get(filePath), cu.toString().getBytes());
        System.out.println("Transformation complete.");
    }
}