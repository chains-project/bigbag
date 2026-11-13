package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.Name;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import java.io.IOException;
import java.nio.file.*;
import java.util.stream.Stream;

public class Main {
    
    private static class JavaxToJakartaJwsVisitor extends ModifierVisitor<Void> {
        
        @Override
        public Node visit(ImportDeclaration importDecl, Void arg) {
            String importName = importDecl.getNameAsString();
            
            if (importName.startsWith("javax.jws")) {
                String newImportName = importName.replaceFirst("^javax\\.jws", "jakarta.jws");
                importDecl.setName(new Name(newImportName));
            }
            
            return super.visit(importDecl, arg);
        }
    }
    
    private static void processFile(Path filePath) throws IOException {
        JavaParser parser = new JavaParser();
        CompilationUnit cu = parser.parse(filePath).getResult().orElseThrow(
            () -> new RuntimeException("Failed to parse " + filePath)
        );
        
        JavaxToJakartaJwsVisitor visitor = new JavaxToJakartaJwsVisitor();
        cu.accept(visitor, null);
        
        Files.write(filePath, cu.toString().getBytes());
        System.out.println("Processed: " + filePath);
    }
    
    private static void processDirectory(Path rootDir) throws IOException {
        try (Stream<Path> paths = Files.walk(rootDir)) {
            paths.filter(Files::isRegularFile)
                 .filter(p -> p.toString().endsWith(".java"))
                 .forEach(file -> {
                     try {
                         processFile(file);
                     } catch (IOException e) {
                         System.err.println("Error processing " + file + ": " + e.getMessage());
                     }
                 });
        }
    }
    
    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Usage: java Main <source-directory>");
            System.err.println("Example: java Main /path/to/project/src");
            System.exit(1);
        }
        
        Path sourceDir = Paths.get(args[0]);
        if (!Files.exists(sourceDir) || !Files.isDirectory(sourceDir)) {
            System.err.println("Error: Source directory does not exist: " + sourceDir);
            System.exit(1);
        }
        
        try {
            System.out.println("Starting javax.jws → jakarta.jws transformation...");
            System.out.println("Processing directory: " + sourceDir.toAbsolutePath());
            processDirectory(sourceDir);
            System.out.println("Transformation completed successfully!");
        } catch (IOException e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}