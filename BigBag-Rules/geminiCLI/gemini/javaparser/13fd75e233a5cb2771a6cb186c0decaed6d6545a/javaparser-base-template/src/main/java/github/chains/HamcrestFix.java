package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.BooleanLiteralExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import com.github.javaparser.utils.SourceRoot;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

public class HamcrestFix {
    public static void main(String[] args) throws IOException {
        Path projectRoot = Paths.get("/workspace/docker-adapter/src/test/java");
        SourceRoot sourceRoot = new SourceRoot(projectRoot);
        
        sourceRoot.parse("", (localPath, absolutePath, result) -> {
            if (result.isSuccessful()) {
                CompilationUnit cu = result.getResult().get();
                boolean changed = false;
                
                FixVisitor visitor = new FixVisitor();
                visitor.visit(cu, null);
                
                // We rely on the visitor modifying the AST in place. 
                // However, SourceRoot.parse callback usually handles saving if we tell it to? 
                // Actually SourceRoot has saveAll().
                // But here we can just save manually if we want, or use SourceRoot capabilities.
                // Simpler: iterate, modify, save.
                return SourceRoot.Callback.Result.SAVE; 
            }
            return SourceRoot.Callback.Result.DONT_SAVE;
        });
    }

    private static class FixVisitor extends ModifierVisitor<Void> {
        @Override
        public Visitable visit(ObjectCreationExpr n, Void arg) {
            if (n.getType().getNameAsString().equals("StringContains") || 
                n.getType().getNameAsString().equals("StringStartsWith")) {
                
                if (n.getArguments().size() == 2 && n.getArgument(0) instanceof BooleanLiteralExpr) {
                    // Remove the first argument (the boolean)
                    n.getArguments().remove(0);
                }
            }
            return super.visit(n, arg);
        }
    }
}
