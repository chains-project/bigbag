package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.BooleanLiteralExpr;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

public class Main {
    public static void main(String[] args) throws IOException {
        Path sourceRoot = Paths.get("/workspace/docker-adapter/src");
        if (!Files.exists(sourceRoot)) {
            System.err.println("Source root does not exist: " + sourceRoot);
            return;
        }

        try (Stream<Path> paths = Files.walk(sourceRoot)) {
            paths.filter(p -> p.toString().endsWith(".java"))
                 .forEach(Main::processFile);
        }
    }

    private static void processFile(Path path) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(path);
            boolean modified = false;

            for (ObjectCreationExpr oce : cu.findAll(ObjectCreationExpr.class)) {
                if (isTargetMatcher(oce)) {
                    // Remove the first argument (boolean)
                    if (oce.getArguments().size() == 2 && oce.getArgument(0) instanceof BooleanLiteralExpr) {
                        oce.getArguments().remove(0);
                        modified = true;
                        System.out.println("Fixed " + oce.getType().getNameAsString() + " in " + path);
                    }
                }
            }

            if (modified) {
                Files.write(path, cu.toString().getBytes());
            }
        } catch (Exception e) {
            System.err.println("Failed to process " + path + ": " + e.getMessage());
        }
    }

    private static boolean isTargetMatcher(ObjectCreationExpr oce) {
        String typeName = oce.getType().getNameAsString();
        return (typeName.equals("StringContains") || typeName.equals("StringStartsWith") ||
                typeName.endsWith(".StringContains") || typeName.endsWith(".StringStartsWith"));
    }
}