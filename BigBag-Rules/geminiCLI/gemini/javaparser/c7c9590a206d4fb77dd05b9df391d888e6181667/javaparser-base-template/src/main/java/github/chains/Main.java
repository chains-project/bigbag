package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Main {
    public static void main(String[] args) throws IOException {
        Path file = Paths.get("/workspace/scoverage-maven-plugin/src/main/java/org/scoverage/plugin/SCoverageReportMojo.java");
        if (!Files.exists(file)) {
            System.err.println("File not found: " + file);
            System.exit(1);
        }
        
        CompilationUnit cu = StaticJavaParser.parse(file);
        
        boolean modified = false;
        for (ImportDeclaration importDecl : cu.getImports()) {
            if (importDecl.getNameAsString().equals("org.apache.maven.doxia.module.xhtml.decoration.render.RenderingContext")) {
                importDecl.setName("org.apache.maven.doxia.siterenderer.RenderingContext");
                modified = true;
            }
        }
        
        if (modified) {
            Files.write(file, cu.toString().getBytes());
            System.out.println("Import updated.");
        } else {
            System.out.println("Import not found or already correct.");
        }
    }
}