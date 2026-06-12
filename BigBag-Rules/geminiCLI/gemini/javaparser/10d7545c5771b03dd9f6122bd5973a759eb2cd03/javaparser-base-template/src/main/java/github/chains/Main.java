package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.stmt.BlockStmt;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;

public class Main {
    public static void main(String[] args) throws Exception {
        String filePath = "/workspace/lithium/src/main/java/com/wire/lithium/Server.java";
        File file = new File(filePath);
        CompilationUnit cu = StaticJavaParser.parse(file);
        
        cu.getImports().removeIf(i -> i.getNameAsString().equals("io.dropwizard.client.JerseyClientBuilder"));

        cu.findAll(MethodDeclaration.class).forEach(method -> {
            if (method.getNameAsString().equals("createHttpClient")) {
                BlockStmt block = StaticJavaParser.parseBlock(
                    "{ return javax.ws.rs.client.ClientBuilder.newClient()\n" +
                    "                .register(MultiPartFeature.class)\n" +
                    "                .register(JacksonJsonProvider.class); }"
                );
                method.setBody(block);
            }
        });
        
        Files.write(Paths.get(filePath), cu.toString().getBytes());
    }
}
