package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Main {
    public static void main(String[] args) throws IOException {
        Path path = Paths.get("/workspace/sorald/sorald/src/main/java/sorald/sonar/SonarLintEngine.java");
        CompilationUnit cu = StaticJavaParser.parse(path);

        cu.accept(new VoidVisitorAdapter<Void>() {
            @Override
            public void visit(MethodDeclaration n, Void arg) {
                if (n.getNameAsString().equals("buildAnalysisEngineConfiguration")) {
                    String newBody = "{\n" +
                            "    return AnalysisEngineConfiguration.builder()\n" +
                            "            .setClientPid(globalConfig.getClientPid())\n" +
                            "            .setExtraProperties(globalConfig.extraProperties())\n" +
                            "            .setWorkDir(globalConfig.getWorkDir())\n" +
                            "            .setModulesProvider(globalConfig.getModulesProvider())\n" +
                            "            .build();\n" +
                            "}";
                    BlockStmt body = StaticJavaParser.parseBlock(newBody);
                    n.setBody(body);
                }
                super.visit(n, arg);
            }
        }, null);

        Files.write(path, cu.toString().getBytes(StandardCharsets.UTF_8));
        System.out.println("Transformation applied.");
    }
}