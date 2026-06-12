package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

public class Main {
    public static void main(String[] args) throws Exception {
        Path projectRoot = Paths.get("/workspace/docker-adapter/src/main/java");
        try (Stream<Path> paths = Files.walk(projectRoot)) {
            paths.filter(Files::isRegularFile)
                 .filter(p -> p.toString().endsWith(".java"))
                 .forEach(Main::processFile);
        }
    }

    private static void processFile(Path filePath) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(filePath);
            boolean changed = false;

            for (MethodCallExpr mc : cu.findAll(MethodCallExpr.class)) {
                if (mc.getNameAsString().equals("asString") && mc.getScope().isPresent()) {
                    Expression scope = mc.getScope().get();
                    if (scope.isObjectCreationExpr()) {
                        ObjectCreationExpr hexOf = scope.asObjectCreationExpr();
                        if (hexOf.getType().getNameAsString().equals("HexOf") && hexOf.getArguments().size() == 1) {
                            Expression arg = hexOf.getArgument(0);
                            if (arg.isObjectCreationExpr()) {
                                ObjectCreationExpr bytesOf = arg.asObjectCreationExpr();
                                if (bytesOf.getType().getNameAsString().equals("BytesOf") && bytesOf.getArguments().size() == 1) {
                                    Expression inner = bytesOf.getArgument(0);
                                    MethodCallExpr newCall = new MethodCallExpr(new NameExpr("org.apache.commons.codec.binary.Hex"), "encodeHexString", new NodeList<>(inner));
                                    mc.replace(newCall);
                                    changed = true;
                                }
                            }
                        }
                    }
                }
            }

            if (cu.getImports().removeIf(i -> i.getNameAsString().equals("org.cactoos.io.BytesOf") || i.getNameAsString().equals("org.cactoos.text.HexOf"))) {
                changed = true;
            }

            if (changed) {
                Files.writeString(filePath, cu.toString());
                System.out.println("Modified: " + filePath);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
