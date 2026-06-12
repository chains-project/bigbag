package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Main {
    public static void main(String[] args) throws Exception {
        Path path = Paths.get("/workspace/sorald/sorald/src/main/java/sorald/sonar/SonarLintEngine.java");
        CompilationUnit cu = StaticJavaParser.parse(path);

        cu.accept(new ModifierVisitor<Void>() {
            @Override
            public Visitable visit(MethodCallExpr n, Void arg) {
                Visitable v = super.visit(n, arg);
                if (v instanceof MethodCallExpr) {
                    MethodCallExpr m = (MethodCallExpr) v;
                    if (m.getNameAsString().equals("addEnabledLanguages")) {
                        return m.getScope().orElse(m);
                    }
                }
                return v;
            }
        }, null);

        Files.write(path, cu.toString().getBytes());
    }
}
