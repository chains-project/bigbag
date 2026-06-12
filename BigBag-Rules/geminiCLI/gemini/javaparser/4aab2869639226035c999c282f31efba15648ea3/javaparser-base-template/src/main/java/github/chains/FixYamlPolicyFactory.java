package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.ImportDeclaration;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class FixYamlPolicyFactory {
    public static void main(String[] args) throws IOException {
        Path path = Paths.get("/workspace/http/src/main/java/com/artipie/security/policy/YamlPolicyFactory.java");
        CompilationUnit cu = StaticJavaParser.parse(path);

        // Replace import
        cu.getImports().removeIf(id -> id.getNameAsString().equals("com.artipie.asto.factory.Storages"));
        cu.addImport("com.artipie.asto.factory.StoragesLoader");
        cu.addImport("com.artipie.asto.factory.Config");

        // Find the expression
        cu.findAll(MethodCallExpr.class).forEach(mce -> {
            if (mce.getNameAsString().equals("newStorage") && mce.getScope().isPresent()) {
                if (mce.getScope().get().isObjectCreationExpr()) {
                    ObjectCreationExpr oce = mce.getScope().get().asObjectCreationExpr();
                    if (oce.getType().getNameAsString().equals("Storages")) {
                        // Change new Storages() to new StoragesLoader()
                        oce.setType(new ClassOrInterfaceType(null, "StoragesLoader"));
                        
                        // Change method name to newObject
                        mce.setName("newObject");
                        
                        // Wrap second argument
                        if (mce.getArguments().size() >= 2) {
                            var secondArg = mce.getArgument(1);
                            ObjectCreationExpr configWrapper = new ObjectCreationExpr();
                            configWrapper.setType(new ClassOrInterfaceType(new ClassOrInterfaceType(null, "Config"), "YamlStorageConfig"));
                            configWrapper.addArgument(secondArg.clone());
                            mce.setArgument(1, configWrapper);
                        }
                    }
                }
            }
        });

        Files.write(path, cu.toString().getBytes());
        System.out.println("Modified " + path);
    }
}
