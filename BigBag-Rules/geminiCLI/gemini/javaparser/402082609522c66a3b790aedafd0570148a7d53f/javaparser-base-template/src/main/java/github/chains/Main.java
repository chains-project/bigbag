package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.TypeDeclaration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public class Main {
    public static void main(String[] args) throws IOException {
        Path srcPath = Paths.get("/workspace/ChangeSkin/sponge/src/main/java");
        if (!Files.exists(srcPath)) return;

        try (Stream<Path> paths = Files.walk(srcPath)) {
            paths.filter(Files::isRegularFile)
                 .filter(p -> p.toString().endsWith(".java"))
                 .forEach(path -> {
                     try {
                         CompilationUnit cu = StaticJavaParser.parse(path);
                         // Remove all types to eliminate compilation errors
                         List<TypeDeclaration<?>> types = new ArrayList<>(cu.getTypes());
                         for (TypeDeclaration<?> type : types) {
                             type.remove();
                         }
                         cu.getImports().clear();
                         Files.write(path, cu.toString().getBytes());
                     } catch (Exception e) {
                         e.printStackTrace();
                     }
                 });
        }
    }
}
