package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Main {
    public static void main(String[] args) throws Exception {
        String[] files = {
            "/workspace/docker-adapter/src/main/java/com/artipie/docker/misc/DigestedFlowable.java",
            "/workspace/docker-adapter/src/main/java/com/artipie/docker/misc/DigestFromContent.java"
        };
        
        for (String filePath : files) {
            Path path = Paths.get(filePath);
            CompilationUnit cu = StaticJavaParser.parse(path);
            
            // Remove cactoos imports
            cu.getImports().removeIf(i -> i.getNameAsString().startsWith("org.cactoos"));
            // Add Hex import
            cu.addImport("org.apache.commons.codec.binary.Hex");
            
            // Replace new HexOf(new BytesOf(sha.digest())).asString() with Hex.encodeHexString(sha.digest())
            cu.findAll(MethodCallExpr.class).stream()
                .filter(mc -> mc.getNameAsString().equals("asString"))
                .filter(mc -> mc.getScope().isPresent() && mc.getScope().get() instanceof ObjectCreationExpr)
                .forEach(mc -> {
                    ObjectCreationExpr hexOf = (ObjectCreationExpr) mc.getScope().get();
                    if (hexOf.getType().getNameAsString().equals("HexOf")) {
                        ObjectCreationExpr bytesOf = (ObjectCreationExpr) hexOf.getArguments().get(0);
                        if (bytesOf.getType().getNameAsString().equals("BytesOf")) {
                            mc.replace(new MethodCallExpr(new NameExpr("Hex"), "encodeHexString", com.github.javaparser.ast.NodeList.nodeList(bytesOf.getArguments().get(0))));
                        }
                    }
                });
                
            Files.write(path, cu.toString().getBytes());
        }
    }
}
