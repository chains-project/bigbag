package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

public class Main {

    public static void main(String[] args) throws IOException {
        // Process all Java files in the project
        Path projectPath = Paths.get("/workspace/billy");
        List<Path> javaFiles = Files.walk(projectPath)
                .filter(path -> path.toString().endsWith(".java"))
                .collect(Collectors.toList());

        for (Path filePath : javaFiles) {
            transformFile(filePath);
        }
    }

    private static void transformFile(Path filePath) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(new FileInputStream(filePath.toFile()));
            
            // Transform imports
            transformImports(cu);
            
            // Save the modified file
            cu.setStorage(filePath);
            
        } catch (Exception e) {
            System.err.println("Error processing file: " + filePath + " - " + e.getMessage());
        }
    }
    
    private static void transformImports(CompilationUnit cu) {
        NodeList<ImportDeclaration> imports = cu.getImports();
        
        // Replace javax.jws imports with jakarta.jws
        for (ImportDeclaration imp : imports) {
            String importName = imp.getNameAsString();
            if (importName.startsWith("javax.jws")) {
                imp.setName(importName.replace("javax.jws", "jakarta.jws"));
            }
            if (importName.startsWith("javax.xml.ws")) {
                imp.setName(importName.replace("javax.xml.ws", "jakarta.xml.ws"));
            }
            if (importName.startsWith("javax.jws.soap")) {
                imp.setName(importName.replace("javax.jws.soap", "jakarta.jws.soap"));
            }
        }
    }
}