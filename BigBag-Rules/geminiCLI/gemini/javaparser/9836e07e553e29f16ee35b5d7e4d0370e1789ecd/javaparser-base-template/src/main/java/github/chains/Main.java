package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.BooleanLiteralExpr;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

public class Main {
    public static void main(String[] args) throws IOException {
        Path projectDir = Paths.get("/workspace/docker-adapter/src/test/java");
        if (!Files.exists(projectDir)) {
            System.err.println("Directory not found: " + projectDir);
            return;
        }
        try (Stream<Path> paths = Files.walk(projectDir)) {
            paths.filter(Files::isRegularFile)
                 .filter(p -> p.toString().endsWith(".java"))
                 .forEach(Main::processFile);
        }
    }

    private static void processFile(Path path) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(path);
            boolean modified = false;
            
            for (ObjectCreationExpr objectCreationExpr : cu.findAll(ObjectCreationExpr.class)) {
                String typeName = objectCreationExpr.getType().getName().getIdentifier();
                if (("StringContains".equals(typeName) || "StringStartsWith".equals(typeName)) &&
                    objectCreationExpr.getArguments().size() == 2 &&
                    objectCreationExpr.getArgument(0) instanceof BooleanLiteralExpr) {
                    
                    objectCreationExpr.getArguments().remove(0);
                    modified = true;
                }
            }

            if (modified) {
                Files.write(path, cu.toString().getBytes());
                System.out.println("Modified: " + path);
            }
        } catch (Exception e) {
            System.err.println("Error processing " + path + ": " + e.getMessage());
        }
    }
}