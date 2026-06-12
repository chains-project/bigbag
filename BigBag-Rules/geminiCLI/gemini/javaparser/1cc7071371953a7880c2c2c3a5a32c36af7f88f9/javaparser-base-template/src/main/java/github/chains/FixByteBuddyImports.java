package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class FixByteBuddyImports {

    public static void main(String[] args) throws IOException {
        Path projectRoot = Paths.get("/workspace/assertj-vavr/src/main/java");
        
        try (Stream<Path> walk = Files.walk(projectRoot)) {
            List<Path> javaFiles = walk.filter(p -> p.toString().endsWith(".java")).collect(Collectors.toList());

            for (Path javaFile : javaFiles) {
                processFile(javaFile);
            }
        }
    }

    private static void processFile(Path javaFile) throws IOException {
        System.out.println("Processing: " + javaFile);
        CompilationUnit cu = StaticJavaParser.parse(javaFile);
        boolean changed = false;

        for (ImportDeclaration importDeclaration : cu.getImports()) {
            String name = importDeclaration.getNameAsString();
            if (name.startsWith("org.assertj.core.internal.bytebuddy")) {
                String newName = name.replace("org.assertj.core.internal.bytebuddy", "net.bytebuddy");
                importDeclaration.setName(newName);
                changed = true;
                System.out.println("  Replaced import: " + name + " -> " + newName);
            }
        }

        if (changed) {
            Files.write(javaFile, cu.toString().getBytes());
            System.out.println("  Saved changes to " + javaFile);
        }
    }
}
