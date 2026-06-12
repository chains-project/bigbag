package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.Expression;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;

public class Main {
    public static void main(String[] args) throws Exception {
        String[] files = {
            "/workspace/docker-adapter/src/main/java/com/artipie/docker/misc/DigestedFlowable.java",
            "/workspace/docker-adapter/src/main/java/com/artipie/docker/misc/DigestFromContent.java"
        };

        for (String filePath : files) {
            File file = new File(filePath);
            CompilationUnit cu = StaticJavaParser.parse(file);

            // Remove imports
            cu.getImports().removeIf(i -> 
                i.getNameAsString().equals("org.cactoos.io.BytesOf") || 
                i.getNameAsString().equals("org.cactoos.text.HexOf"));
            
            // Add new import
            cu.addImport("org.apache.commons.codec.binary.Hex");

            cu.findAll(MethodCallExpr.class).forEach(mc -> {
                if (mc.getNameAsString().equals("asString")) {
                    if (mc.getScope().isPresent() && mc.getScope().get() instanceof ObjectCreationExpr) {
                        ObjectCreationExpr hexOf = (ObjectCreationExpr) mc.getScope().get();
                        if (hexOf.getType().getNameAsString().equals("HexOf")) {
                            if (hexOf.getArguments().size() == 1 && hexOf.getArgument(0) instanceof ObjectCreationExpr) {
                                ObjectCreationExpr bytesOf = (ObjectCreationExpr) hexOf.getArgument(0);
                                if (bytesOf.getType().getNameAsString().equals("BytesOf")) {
                                    Expression arg = bytesOf.getArgument(0);
                                    Expression replacement = StaticJavaParser.parseExpression("Hex.encodeHexString(" + arg.toString() + ")");
                                    mc.replace(replacement);
                                }
                            }
                        }
                    }
                }
            });

            Files.write(Paths.get(filePath), cu.toString().getBytes());
        }
    }
}
