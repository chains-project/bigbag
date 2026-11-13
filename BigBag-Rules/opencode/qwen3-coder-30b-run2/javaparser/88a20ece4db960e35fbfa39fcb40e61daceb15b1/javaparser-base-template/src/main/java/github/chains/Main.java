package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source_directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        Path dir = Paths.get(sourceDir);
        
        try {
            // Traverse all Java files in the directory
            Files.walk(dir)
                .filter(path -> path.toString().endsWith(".java"))
                .forEach(Main::processFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    private static void processFile(Path filePath) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(filePath.toFile());
            
            // Transform: Replace PublishMetadata with MessageMetadata
            cu.accept(new VoidVisitorAdapter<Void>() {
                @Override
                public void visit(ImportDeclaration importDecl, Void arg) {
                    super.visit(importDecl, arg);
                    if (importDecl.getNameAsString().equals("com.google.cloud.pubsublite.PublishMetadata")) {
                        importDecl.setName("com.google.cloud.pubsublite.MessageMetadata");
                    }
                }

                @Override
                public void visit(ClassOrInterfaceType type, Void arg) {
                    super.visit(type, arg);
                    if (type.getNameAsString().equals("PublishMetadata")) {
                        type.setName("MessageMetadata");
                    }
                }
            }, null);
            
            // Write back the modified file
            Files.write(filePath, cu.toString().getBytes());
            System.out.println("Processed: " + filePath);
            
        } catch (Exception e) {
            System.err.println("Error processing " + filePath + ": " + e.getMessage());
        }
    }
}