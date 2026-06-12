package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

public class Main {
    public static void main(String[] args) throws IOException {
        File file = new File("/workspace/files-adapter/src/test/java/com/artipie/files/FileProxySliceTest.java");
        CompilationUnit cu = StaticJavaParser.parse(file);

        // Remove the MapEntry import
        cu.getImports().removeIf(i -> i.getNameAsString().equals("org.cactoos.map.MapEntry"));

        // Replace MapEntry instantiations
        cu.findAll(ObjectCreationExpr.class).forEach(oce -> {
            if (oce.getType().getNameAsString().equals("MapEntry")) {
                oce.getType().setName("java.util.AbstractMap.SimpleImmutableEntry");
            }
        });

        Files.writeString(file.toPath(), cu.toString());
    }
}
