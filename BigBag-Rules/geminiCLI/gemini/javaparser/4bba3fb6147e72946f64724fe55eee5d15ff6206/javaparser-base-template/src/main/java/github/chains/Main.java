package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

public class Main {
    public static void main(String[] args) throws IOException {
        Path projectRoot = Paths.get("/workspace/cdi-test");
        try (Stream<Path> paths = Files.walk(projectRoot)) {
            paths.filter(Files::isRegularFile)
                 .filter(p -> p.toString().endsWith(".java"))
                 .forEach(Main::processJavaFile);
        }
    }

    private static void processJavaFile(Path filePath) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(filePath);
            boolean modified = false;

            for (ImportDeclaration id : cu.findAll(ImportDeclaration.class)) {
                String name = id.getNameAsString();
                if (name.startsWith("javax.annotation")) {
                    id.setName(name.replace("javax.annotation", "jakarta.annotation"));
                    modified = true;
                }
            }

            if (modified) {
                Files.writeString(filePath, cu.toString());
                System.out.println("Modified: " + filePath);
            }
        } catch (Exception e) {
            System.err.println("Failed to parse/process: " + filePath + " - " + e.getMessage());
        }
    }
}
