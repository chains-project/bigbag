package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import com.github.javaparser.utils.SourceRoot;
import java.nio.file.Paths;
import java.nio.file.Path;
import java.io.IOException;

public class Main {
    public static void main(String[] args) throws IOException {
        Path path = Paths.get("/workspace/fluxtion/compiler/src/test/java");
        SourceRoot sourceRoot = new SourceRoot(path);
        
        sourceRoot.tryToParse();

        for (CompilationUnit cu : sourceRoot.getCompilationUnits()) {
            new ModifierVisitor<Void>() {
                @Override
                public Visitable visit(ObjectCreationExpr n, Void arg) {
                    if (n.getType().getNameAsString().equals("Constructor")) {
                        if (n.getArguments().isEmpty()) {
                            n.addArgument(StaticJavaParser.parseExpression("new org.yaml.snakeyaml.LoaderOptions()"));
                        }
                    }
                    return super.visit(n, arg);
                }
            }.visit(cu, null);
        }

        sourceRoot.saveAll();
    }
}
