package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

public class FixHamcrestConstructors {
    public static void main(String[] args) throws IOException {
        Path projectRoot = Paths.get("/workspace/docker-adapter/src/test/java");
        
        try (Stream<Path> paths = Files.walk(projectRoot)) {
            paths.filter(p -> p.toString().endsWith(".java")).forEach(path -> {
                try {
                    CompilationUnit cu = StaticJavaParser.parse(path);
                    ChangedVisitor visitor = new ChangedVisitor();
                    visitor.visit(cu, null);
                    
                    if (visitor.isChanged()) {
                        Files.write(path, cu.toString().getBytes());
                        System.out.println("Modified: " + path);
                    }
                } catch (IOException e) {
                    System.err.println("Error processing " + path + ": " + e.getMessage());
                }
            });
        }
    }

    private static class ChangedVisitor extends ModifierVisitor<Void> {
        private boolean changed = false;

        public boolean isChanged() {
            return changed;
        }

        @Override
        public Visitable visit(ObjectCreationExpr n, Void arg) {
            if (n.getType().getNameAsString().equals("StringContains") || 
                n.getType().getNameAsString().equals("StringStartsWith")) {
                if (n.getArguments().size() == 2 && n.getArgument(0).isBooleanLiteralExpr()) {
                    n.getArguments().remove(0);
                    changed = true;
                }
            }
            return super.visit(n, arg);
        }
    }
}
