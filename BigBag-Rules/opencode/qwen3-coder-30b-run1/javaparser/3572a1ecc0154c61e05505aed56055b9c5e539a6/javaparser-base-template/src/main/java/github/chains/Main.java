package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Main {
    public static void main(String[] args) throws IOException {
        // Process all Java files in the source directory
        String sourceDir = args.length > 0 ? args[0] : "/workspace/singer";
        Path sourcePath = Paths.get(sourceDir);
        
        // Find all Java files
        Files.walk(sourcePath)
                .filter(path -> path.toString().endsWith(".java"))
                .forEach(Main::processFile);
    }
    
    private static void processFile(Path filePath) {
        try {
            // Parse the Java file
            CompilationUnit cu = StaticJavaParser.parse(filePath.toFile());
            
            // Apply transformation to fix TFastFramedTransport and TFramedTransport issues
            new TFramedTransportReplacer().visit(cu, null);
            
            // Save the modified file
            try (FileWriter writer = new FileWriter(filePath.toFile())) {
                writer.write(cu.toString());
            }
            
        } catch (IOException e) {
            System.err.println("Error processing file " + filePath + ": " + e.getMessage());
        }
    }
    
    /**
     * Visitor to fix TFastFramedTransport and TFramedTransport issues
     */
    private static class TFramedTransportReplacer extends VoidVisitorAdapter<Void> {
        
        @Override
        public void visit(ImportDeclaration importDecl, Void arg) {
            super.visit(importDecl, arg);
            
            // Replace imports for both TFastFramedTransport and TFramedTransport
            if (importDecl.getNameAsString().equals("org.apache.thrift.transport.TFastFramedTransport")) {
                importDecl.setName("org.apache.thrift.transport.layered.TFastFramedTransport");
            } else if (importDecl.getNameAsString().equals("org.apache.thrift.transport.TFramedTransport")) {
                importDecl.setName("org.apache.thrift.transport.layered.TFramedTransport");
            }
        }
        
        @Override
        public void visit(ClassOrInterfaceDeclaration classDecl, Void arg) {
            super.visit(classDecl, arg);
        }
        
        @Override
        public void visit(ObjectCreationExpr objectCreationExpr, Void arg) {
            super.visit(objectCreationExpr, arg);
            
            // Check if this is a TFastFramedTransport instantiation
            if (objectCreationExpr.getType().getNameAsString().equals("TFastFramedTransport")) {
                // Replace with TFastFramedTransport from layered package
                objectCreationExpr.setType("org.apache.thrift.transport.layered.TFastFramedTransport");
            } else if (objectCreationExpr.getType().getNameAsString().equals("TFramedTransport")) {
                // Replace with TFramedTransport from layered package
                objectCreationExpr.setType("org.apache.thrift.transport.layered.TFramedTransport");
            }
        }
    }
}