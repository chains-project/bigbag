package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;

public class Main {
    public static void main(String[] args) throws Exception {
        File file = new File("/workspace/simplelocalize-cli/src/main/java/io/simplelocalize/cli/configuration/ConfigurationLoader.java");
        CompilationUnit cu = StaticJavaParser.parse(new FileInputStream(file));

        cu.accept(new VoidVisitorAdapter<Void>() {
            @Override
            public void visit(ObjectCreationExpr n, Void arg) {
                super.visit(n, arg);
                if (n.getType().getNameAsString().equals("Constructor") && n.getArguments().size() == 1) {
                    n.addArgument(StaticJavaParser.parseExpression("new org.yaml.snakeyaml.LoaderOptions()"));
                }
            }
        }, null);

        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(cu.toString().getBytes());
        }
    }
}
