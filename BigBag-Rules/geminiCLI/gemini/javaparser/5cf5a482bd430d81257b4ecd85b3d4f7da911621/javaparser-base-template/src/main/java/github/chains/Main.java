package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Main {
    public static void main(String[] args) throws IOException {
        Path projectRoot = Paths.get("/workspace/jakartaee-mvc-sample/src");
        
        try (Stream<Path> paths = Files.walk(projectRoot)) {
            List<Path> javaFiles = paths
                .filter(Files::isRegularFile)
                .filter(p -> p.toString().endsWith(".java"))
                .collect(Collectors.toList());

            for (Path javaFile : javaFiles) {
                CompilationUnit cu = StaticJavaParser.parse(javaFile);
                LexicalPreservingPrinter.setup(cu);
                boolean changed = false;
                for (ImportDeclaration id : cu.findAll(ImportDeclaration.class)) {
                    String name = id.getNameAsString();
                    if (name.startsWith("javax.mvc")) {
                        id.setName(name.replace("javax.mvc", "jakarta.mvc"));
                        changed = true;
                    }
                }
                if (changed) {
                    Files.writeString(javaFile, LexicalPreservingPrinter.print(cu));
                    System.out.println("Modified " + javaFile);
                }
            }
        }
    }
}
