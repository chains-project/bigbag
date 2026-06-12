package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;

import java.io.File;
import java.io.FileWriter;
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
            System.exit(1);
        }
        
        String sourceDirectory = args[0];
        Path sourcePath = Paths.get(sourceDirectory);
        
        if (!Files.exists(sourcePath)) {
            System.out.println("Source directory does not exist: " + sourceDirectory);
            System.exit(1);
        }
        
        // Process all Java files in the directory recursively
        try (Stream<Path> paths = Files.walk(sourcePath)
                .filter(Files::isRegularFile)
                .filter(path -> path.toString().endsWith(".java"))) {
            
            List<Path> javaFiles = paths.collect(Collectors.toList());
            
            for (Path javaFile : javaFiles) {
                processJavaFile(javaFile);
            }
        }
        
        System.out.println("Transformation completed for all Java files in " + sourceDirectory);
    }
    
    private static void processJavaFile(Path javaFile) throws IOException {
        CompilationUnit cu = StaticJavaParser.parse(javaFile);
        
        boolean modified = false;
        
        // Transform all imports that start with javax.* packages that moved to jakarta.*
        for (ImportDeclaration importDecl : cu.getImports()) {
            String importName = importDecl.getNameAsString();
            
            // Handle the main Jakarta EE migration
            if (importName.startsWith("javax.interceptor")) {
                String newImport = importName.replace("javax.interceptor", "jakarta.interceptor");
                importDecl.setName(newImport);
                modified = true;
            } else if (importName.startsWith("javax.annotation")) {
                String newImport = importName.replace("javax.annotation", "jakarta.annotation");
                importDecl.setName(newImport);
                modified = true;
            } else if (importName.startsWith("javax.enterprise.context")) {
                String newImport = importName.replace("javax.enterprise.context", "jakarta.enterprise.context");
                importDecl.setName(newImport);
                modified = true;
            } else if (importName.startsWith("javax.enterprise.inject")) {
                String newImport = importName.replace("javax.enterprise.inject", "jakarta.enterprise.inject");
                importDecl.setName(newImport);
                modified = true;
            } else if (importName.startsWith("javax.inject")) {
                String newImport = importName.replace("javax.inject", "jakarta.inject");
                importDecl.setName(newImport);
                modified = true;
            }
        }
        
        // Save the updated file if there were changes
        if (modified) {
            try (FileWriter writer = new FileWriter(javaFile.toFile())) {
                writer.write(cu.toString());
            }
        }
    }
}