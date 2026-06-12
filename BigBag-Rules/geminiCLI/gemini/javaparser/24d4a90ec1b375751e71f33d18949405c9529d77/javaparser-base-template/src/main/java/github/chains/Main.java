package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.IntegerLiteralExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

public class Main {
    public static void main(String[] args) throws IOException {
        Path projectDir = Paths.get("/workspace/jcabi-s3/src/test/java");
        try (Stream<Path> paths = Files.walk(projectDir)) {
            paths
                .filter(Files::isRegularFile)
                .filter(p -> p.toString().endsWith(".java"))
                .forEach(Main::process);
        }
    }

    private static void process(Path path) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(path);
            
            // Use a wrapper to track changes
            final boolean[] changed = {false};

            // Remove import
            if (cu.getImports().removeIf(id -> "com.jcabi.aspects.Tv".equals(id.getNameAsString()))) {
                changed[0] = true;
            }

            // Replace Tv usage
            ModifierVisitor<Void> visitor = new ModifierVisitor<Void>() {
                @Override
                public Visitable visit(FieldAccessExpr n, Void arg) {
                    if (n.getScope().isNameExpr() && ((NameExpr)n.getScope()).getNameAsString().equals("Tv")) {
                        String name = n.getNameAsString();
                        if ("FIVE".equals(name)) {
                            changed[0] = true;
                            return new IntegerLiteralExpr("5");
                        } else if ("HUNDRED".equals(name)) {
                            changed[0] = true;
                            return new IntegerLiteralExpr("100");
                        } else if ("THOUSAND".equals(name)) {
                            changed[0] = true;
                            return new IntegerLiteralExpr("1000");
                        }
                    }
                    return super.visit(n, arg);
                }
            };
            
            visitor.visit(cu, null);

            if (changed[0]) {
                Files.write(path, cu.toString().getBytes());
                System.out.println("Fixed " + path);
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}