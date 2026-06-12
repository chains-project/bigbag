package github.chains;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import com.github.javaparser.utils.SourceRoot;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicBoolean;

public class Main {
    public static void main(String[] args) throws IOException {
        Path projectRoot = Paths.get("/workspace/docker-adapter/src/test/java");
        SourceRoot sourceRoot = new SourceRoot(projectRoot);
        
        sourceRoot.parse("", (localPath, absolutePath, result) -> {
            if (result.isSuccessful()) {
                CompilationUnit cu = result.getResult().get();
                AtomicBoolean modified = new AtomicBoolean(false);

                cu.accept(new ModifierVisitor<Void>() {
                    @Override
                    public Visitable visit(ObjectCreationExpr n, Void arg) {
                        if (n.getType().getNameAsString().equals("AuthUser")) {
                            if (n.getArguments().size() == 1) {
                                n.addArgument(new StringLiteralExpr(""));
                                modified.set(true);
                            }
                        }
                        return super.visit(n, arg);
                    }
                }, null);

                if (modified.get()) {
                    return SourceRoot.Callback.Result.SAVE;
                }
            } else {
                System.err.println("Failed to parse: " + absolutePath);
                result.getProblems().forEach(System.err::println);
            }
            return SourceRoot.Callback.Result.DONT_SAVE;
        });
    }
}
