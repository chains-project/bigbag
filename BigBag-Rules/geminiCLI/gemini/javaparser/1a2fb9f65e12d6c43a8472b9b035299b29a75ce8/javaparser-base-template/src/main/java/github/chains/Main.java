package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

public class Main {
    public static void main(String[] args) throws Exception {
        Path startPath = Paths.get("/workspace/wicket-crudifier/src");
        try (Stream<Path> stream = Files.walk(startPath)) {
            stream.filter(Files::isRegularFile)
                  .filter(p -> p.toString().endsWith(".java"))
                  .forEach(Main::processFile);
        }
    }

    private static void processFile(Path path) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(path);
            boolean modified = false;
            
            NodeList<ImportDeclaration> imports = cu.getImports();
            for (ImportDeclaration id : imports) {
                String name = id.getNameAsString();
                if (name.startsWith("javax.validation")) {
                    id.setName(name.replace("javax.validation", "jakarta.validation"));
                    modified = true;
                }
            }
            
            for (MethodCallExpr mce : cu.findAll(MethodCallExpr.class)) {
                if (mce.getNameAsString().equals("getValidator") && mce.getScope().isPresent()) {
                    Expression scope = mce.getScope().get();
                    if (scope.toString().equals("HibernateValidatorProperty.validatorFactory")) {
                        Expression newExpr = StaticJavaParser.parseExpression("jakarta.validation.Validation.buildDefaultValidatorFactory().getValidator()");
                        mce.replace(newExpr);
                        modified = true;
                    }
                }
            }

            if (modified) {
                Files.write(path, cu.toString().getBytes());
                System.out.println("Modified " + path);
            }
        } catch (Exception e) {
            System.err.println("Error processing file: " + path);
            e.printStackTrace();
        }
    }
}
