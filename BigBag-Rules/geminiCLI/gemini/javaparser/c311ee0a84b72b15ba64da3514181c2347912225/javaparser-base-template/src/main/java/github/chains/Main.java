package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.BooleanLiteralExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

public class Main {
    public static void main(String[] args) throws IOException {
        Path sourceRoot = Paths.get("/workspace/docker-adapter/src/test/java");
        try (Stream<Path> paths = Files.walk(sourceRoot)) {
            paths.filter(p -> p.toString().endsWith(".java"))
                 .forEach(Main::processFile);
        }
    }

    private static void processFile(Path path) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(path);
            boolean[] changed = {false};
            
            cu.accept(new VoidVisitorAdapter<Void>() {
                @Override
                public void visit(ObjectCreationExpr n, Void arg) {
                    super.visit(n, arg);
                    String typeName = n.getType().getName().getIdentifier();
                    if (("StringContains".equals(typeName) || "StringStartsWith".equals(typeName)) && n.getArguments().size() == 2) {
                        if (n.getArgument(0) instanceof BooleanLiteralExpr) {
                            n.getArguments().remove(0);
                            changed[0] = true;
                        }
                    }
                }
            }, null);

            if (changed[0]) {
                Files.write(path, cu.toString().getBytes());
                System.out.println("Modified: " + path);
            }
        } catch (IOException e) {
            System.err.println("Failed to process " + path + ": " + e.getMessage());
        }
    }
}