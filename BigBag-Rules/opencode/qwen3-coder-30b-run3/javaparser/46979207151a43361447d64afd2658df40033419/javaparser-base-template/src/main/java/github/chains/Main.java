package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.BooleanLiteralExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.visitor.ModifierVisitor;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

public class Main {
    public static void main(String[] args) {
        // Process all Java files in the project
        String sourceDir = args.length > 0 ? args[0] : "/workspace/billy";
        
        try {
            // Find all Java files
            List<Path> javaFiles = Files.walk(Paths.get(sourceDir))
                .filter(path -> path.toString().endsWith(".java"))
                .collect(Collectors.toList());
            
            for (Path file : javaFiles) {
                processJavaFile(file);
            }
            
            System.out.println("Transformation completed on " + javaFiles.size() + " files");
        } catch (IOException e) {
            System.err.println("Error processing files: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private static void processJavaFile(Path filePath) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(filePath.toFile());
            
            // Apply transformations to fix jaxb2-basics breaking changes
            cu.accept(new Jaxb2BasicsFixVisitor(), null);
            
            // Write back to file
            try (FileWriter writer = new FileWriter(filePath.toFile())) {
                writer.write(cu.toString());
            }
        } catch (Exception e) {
            System.err.println("Error processing file " + filePath + ": " + e.getMessage());
        }
    }
    
    /**
     * Visitor to fix jaxb2-basics CopyStrategy breaking changes
     * This transforms calls to copy methods that gained an extra boolean parameter
     */
    private static class Jaxb2BasicsFixVisitor extends ModifierVisitor<Void> {
        @Override
        public com.github.javaparser.ast.visitor.Visitable visit(MethodCallExpr n, Void arg) {
            // Check if this is a copy method call that needs fixing
            String methodName = n.getNameAsString();
            
            // Most likely scenario: copy method calls with 2 arguments that should have 3
            if (methodName.equals("copy") && n.getArguments().size() == 2) {
                // Add the missing boolean parameter to maintain backward compatibility
                // This fixes the breaking change from CopyStrategy to CopyStrategy2
                n.getArguments().add(new BooleanLiteralExpr(true));
            }
            
            return super.visit(n, arg);
        }
    }
}