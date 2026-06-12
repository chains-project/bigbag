package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

public class Main {
    public static void main(String[] args) throws Exception {
        Path projectRoot = Paths.get("/workspace/jakartaee-mvc-sample/src/main/java");
        try (Stream<Path> paths = Files.walk(projectRoot)) {
            paths.filter(Files::isRegularFile)
                 .filter(p -> p.toString().endsWith(".java"))
                 .forEach(Main::processJavaFile);
        }
    }

    private static void processJavaFile(Path path) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(path);
            boolean changed = false;
            for (ImportDeclaration id : cu.findAll(ImportDeclaration.class)) {
                String importName = id.getNameAsString();
                if (importName.startsWith("javax.mvc")) {
                    id.setName(importName.replaceFirst("javax\\.mvc", "jakarta.mvc"));
                    changed = true;
                }
            }
            if (changed) {
                Files.write(path, cu.toString().getBytes());
                System.out.println("Updated: " + path);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
