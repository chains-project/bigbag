package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Main {
    public static void main(String[] args) throws Exception {
        String[] files = {
            "/workspace/docker-adapter/src/main/java/com/artipie/docker/misc/DigestFromContent.java",
            "/workspace/docker-adapter/src/main/java/com/artipie/docker/misc/DigestedFlowable.java"
        };
        for (String file : files) {
            Path path = Paths.get(file);
            CompilationUnit cu = StaticJavaParser.parse(path);
            cu.accept(new VoidVisitorAdapter<Void>() {
                @Override
                public void visit(MethodCallExpr n, Void arg) {
                    super.visit(n, arg);
                    if (n.getNameAsString().equals("asString") && n.getScope().isPresent()) {
                        if (n.getScope().get().isObjectCreationExpr()) {
                            ObjectCreationExpr oce = n.getScope().get().asObjectCreationExpr();
                            if (oce.getType().getNameAsString().equals("HexOf")) {
                                if (oce.getArguments().size() == 1 && oce.getArgument(0).isObjectCreationExpr()) {
                                    ObjectCreationExpr bytesOf = oce.getArgument(0).asObjectCreationExpr();
                                    if (bytesOf.getType().getNameAsString().equals("BytesOf")) {
                                        n.replace(StaticJavaParser.parseExpression(
                                            "org.apache.commons.codec.binary.Hex.encodeHexString(" + bytesOf.getArgument(0).toString() + ")"
                                        ));
                                    }
                                }
                            }
                        }
                    }
                }
            }, null);

            cu.getImports().removeIf(i -> i.getNameAsString().equals("org.cactoos.io.BytesOf"));
            cu.getImports().removeIf(i -> i.getNameAsString().equals("org.cactoos.text.HexOf"));

            Files.writeString(path, cu.toString());
        }
    }
}