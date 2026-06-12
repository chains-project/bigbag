package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.MethodCallExpr;

import java.nio.file.Files;
import java.nio.file.Paths;

public class Main {
    public static void main(String[] args) throws Exception {
        String filePath = "/workspace/sorald/sorald/src/main/java/sorald/sonar/SonarLintEngine.java";
        CompilationUnit cu = StaticJavaParser.parse(Paths.get(filePath));
        
        cu.findAll(MethodCallExpr.class, n -> n.getNameAsString().equals("addEnabledLanguages")).forEach(n -> {
            n.getScope().ifPresent(scope -> n.replace(scope));
        });
        
        Files.writeString(Paths.get(filePath), cu.toString());
    }
}
