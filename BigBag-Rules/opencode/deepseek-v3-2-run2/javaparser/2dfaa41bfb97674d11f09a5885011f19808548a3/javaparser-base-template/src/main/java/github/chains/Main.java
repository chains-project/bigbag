package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

public class Main {
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar javaparser.jar <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Processing source directory: " + sourceDir);
        
        try {
            processDirectory(Paths.get(sourceDir));
            System.out.println("Transformation completed successfully!");
        } catch (IOException e) {
            System.err.println("Error processing directory: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static void processDirectory(Path directory) throws IOException {
        try (Stream<Path> paths = Files.walk(directory)) {
            paths.filter(Files::isRegularFile)
                 .filter(path -> path.toString().endsWith(".java"))
                 .forEach(Main::processFile);
        }
    }
    
    private static void processFile(Path filePath) {
        try (FileInputStream input = new FileInputStream(filePath.toFile())) {
            JavaParser parser = new JavaParser();
            CompilationUnit cu = parser.parse(input).getResult().orElse(null);
            
            if (cu != null) {
                GetArgumentAtToGetArgumentTransformer transformer = new GetArgumentAtToGetArgumentTransformer();
                cu.accept(transformer, null);
                
                // Check if any modifications were made by examining the transformed AST
                // For simplicity, we'll always write the file since we can't easily track modifications
                System.out.println("Processing: " + filePath);
                try (FileOutputStream output = new FileOutputStream(filePath.toFile())) {
                    output.write(cu.toString().getBytes());
                }
            }
        } catch (IOException e) {
            System.err.println("Error processing file " + filePath + ": " + e.getMessage());
        }
    }
    
    private static class GetArgumentAtToGetArgumentTransformer extends ModifierVisitor<Void> {
        
        @Override
        public MethodCallExpr visit(MethodCallExpr n, Void arg) {
            // Check if this is a call to getArgumentAt method
            if ("getArgumentAt".equals(n.getNameAsString())) {
                // Replace method name from getArgumentAt to getArgument
                n.setName("getArgument");
                System.out.println("  - Replaced getArgumentAt with getArgument");
            }
            
            return (MethodCallExpr) super.visit(n, arg);
        }
    }
}