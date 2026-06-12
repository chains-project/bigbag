package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.Name;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Main {
    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.out.println("Usage: java Main <source_directory>");
            return;
        }
        
        String sourceDirectory = args[0];
        Path sourcePath = Paths.get(sourceDirectory);
        
        // Process all Java files in the directory recursively
        try (Stream<Path> paths = Files.walk(sourcePath)) {
            List<Path> javaFiles = paths
                .filter(Files::isRegularFile)
                .filter(path -> path.toString().endsWith(".java"))
                .collect(Collectors.toList());
            
            for (Path javaFile : javaFiles) {
                processJavaFile(javaFile);
            }
        }
        
        System.out.println("Transformation complete.");
    }
    
    private static void processJavaFile(Path javaFile) throws IOException {
        String content = new String(Files.readAllBytes(javaFile));
        CompilationUnit cu = StaticJavaParser.parse(content);
        
        boolean modified = false;
        
        // Replace javax.validation imports with jakarta.validation
        NodeList<ImportDeclaration> imports = cu.getImports();
        for (ImportDeclaration importDecl : imports) {
            Name name = importDecl.getName();
            String importStr = name.asString();
            if (importStr.startsWith("javax.validation")) {
                String newImport = importStr.replace("javax.validation", "jakarta.validation");
                importDecl.setName(newImport);
                modified = true;
            }
        }
        
        // Handle direct references to javax.validation classes
        for (Name name : cu.findAll(Name.class)) {
            String nameStr = name.asString();
            if (nameStr.startsWith("javax.validation")) {
                String newName = nameStr.replace("javax.validation", "jakarta.validation");
                name.setIdentifier(newName);
                modified = true;
            }
        }
        
        // Handle direct references to javax.validation.constraints
        for (Name name : cu.findAll(Name.class)) {
            String nameStr = name.asString();
            if (nameStr.startsWith("javax.validation.constraints")) {
                String newName = nameStr.replace("javax.validation.constraints", "jakarta.validation.constraints");
                name.setIdentifier(newName);
                modified = true;
            }
        }
        
        // Handle direct references to javax.validation.metadata
        for (Name name : cu.findAll(Name.class)) {
            String nameStr = name.asString();
            if (nameStr.startsWith("javax.validation.metadata")) {
                String newName = nameStr.replace("javax.validation.metadata", "jakarta.validation.metadata");
                name.setIdentifier(newName);
                modified = true;
            }
        }
        
        if (modified) {
            // Print the modified file
            String output = LexicalPreservingPrinter.print(cu);
            Files.write(javaFile, output.getBytes());
            System.out.println("Modified: " + javaFile);
        }
    }
}