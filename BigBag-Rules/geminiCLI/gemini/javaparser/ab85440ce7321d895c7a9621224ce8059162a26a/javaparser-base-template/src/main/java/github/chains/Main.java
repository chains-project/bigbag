package github.chains;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.BooleanLiteralExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.utils.SourceRoot;

import java.nio.file.Paths;
import java.nio.file.Path;

public class Main {
    public static void main(String[] args) throws Exception {
        Path projectRoot = Paths.get("/workspace/docker-adapter/src/test/java");
        SourceRoot sourceRoot = new SourceRoot(projectRoot);
        sourceRoot.parse("", (localPath, absolutePath, result) -> {
            if (result.isSuccessful()) {
                CompilationUnit cu = result.getResult().get();
                
                MyVisitor visitor = new MyVisitor();
                visitor.visit(cu, null);
                
                if (visitor.modified) {
                    return SourceRoot.Callback.Result.SAVE;
                }
            }
            return SourceRoot.Callback.Result.DONT_SAVE;
        });
    }

    static class MyVisitor extends com.github.javaparser.ast.visitor.VoidVisitorAdapter<Void> {
        public boolean modified = false;
        @Override
        public void visit(ObjectCreationExpr n, Void arg) {
            if (n.getType().getNameAsString().equals("StringContains") ||
                n.getType().getNameAsString().equals("StringStartsWith")) {
                if (n.getArguments().size() == 2 && n.getArgument(0) instanceof BooleanLiteralExpr) {
                    n.getArguments().remove(0);
                    modified = true;
                }
            }
            super.visit(n, arg);
        }
    }
}