package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.NodeList;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source_directory>");
            System.exit(1);
        }

        String sourceDir = args[0];
        try {
            processDirectory(sourceDir);
            System.out.println("Transformation completed successfully.");
        } catch (IOException e) {
            System.err.println("Error processing files: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static void processDirectory(String dirPath) throws IOException {
        Path dir = Paths.get(dirPath);
        Files.walk(dir)
                .filter(path -> path.toString().endsWith(".java"))
                .forEach(Main::processJavaFile);
    }

    private static void processJavaFile(Path filePath) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(filePath.toFile());
            
            // Transform imports from javax.ws.rs to jakarta.ws.rs
            transformImports(cu);
            
            // Save the modified file
            try (FileWriter writer = new FileWriter(filePath.toFile())) {
                writer.write(cu.toString());
            }
        } catch (Exception e) {
            System.err.println("Error processing file " + filePath + ": " + e.getMessage());
        }
    }

    private static void transformImports(CompilationUnit cu) {
        NodeList<ImportDeclaration> imports = cu.getImports();
        
        for (ImportDeclaration imp : imports) {
            // Check if it's a javax.ws.rs import
            if (imp.getNameAsString().startsWith("javax.ws.rs")) {
                // Replace with jakarta.ws.rs equivalent
                String newImport = imp.getNameAsString().replace("javax.ws.rs", "jakarta.ws.rs");
                imp.setName(newImport);
            }
        }
    }
}
