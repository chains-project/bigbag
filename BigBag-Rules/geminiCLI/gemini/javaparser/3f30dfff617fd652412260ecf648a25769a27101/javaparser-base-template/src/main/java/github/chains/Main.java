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
        Path startPath = Paths.get("/workspace/cdi-test");
        
        try (Stream<Path> paths = Files.walk(startPath)) {
            paths.filter(Files::isRegularFile)
                 .filter(p -> p.toString().endsWith(".java"))
                 .forEach(Main::processFile);
        }
    }

    private static void processFile(Path filePath) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(filePath);
            boolean modified = false;

            for (ImportDeclaration id : cu.findAll(ImportDeclaration.class)) {
                String importName = id.getNameAsString();
                if (importName.startsWith("javax.interceptor")) {
                    String newName = importName.replace("javax.interceptor", "jakarta.interceptor");
                    id.setName(newName);
                    modified = true;
                }
            }

            if (modified) {
                Files.writeString(filePath, cu.toString());
                System.out.println("Modified: " + filePath);
            }
        } catch (Exception e) {
            System.err.println("Error parsing " + filePath + ": " + e.getMessage());
        }
    }
}
