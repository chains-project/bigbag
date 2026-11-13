package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.github.javaparser.ParseResult;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

public class Main {
    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source_directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        Path dir = Paths.get(sourceDir);
        
        // Walk through all Java files in the directory
        Files.walk(dir)
            .filter(path -> path.toString().endsWith(".java"))
            .forEach(Main::processJavaFile);
    }
    
    private static void processJavaFile(Path javaFilePath) {
        try {
            // Read file content
            String content = Files.readString(javaFilePath);
            
            // Parse the Java file
            ParseResult<CompilationUnit> result = StaticJavaParser.parse(content);
            
            if (result.isSuccessful()) {
                CompilationUnit cu = result.getResult().get();
                
                // Find and fix Yaml constructor calls
                YamlConstructorFixVisitor visitor = new YamlConstructorFixVisitor();
                visitor.visit(cu, null);
                
                // Save the modified file
                String newContent = cu.toString();
                Files.writeString(javaFilePath, newContent);
                
                System.out.println("Processed: " + javaFilePath);
            }
        } catch (Exception e) {
            System.err.println("Error processing file " + javaFilePath + ": " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Visitor that finds Yaml constructor calls and fixes them to use the new API
     */
    private static class YamlConstructorFixVisitor extends VoidVisitorAdapter<Void> {
        @Override
        public void visit(ObjectCreationExpr n, Void arg) {
            super.visit(n, arg);
            
            // Check if this is a Yaml constructor call
            if (n.getType().isClassOrInterfaceType()) {
                var type = n.getType().asClassOrInterfaceType();
                if ("Yaml".equals(type.getNameAsString())) {
                    // Check if it's from snakeyaml package
                    if (type.getScope().isPresent() && 
                        type.getScope().get().asString().endsWith("snakeyaml")) {
                        
                        // If it's a call with no arguments (the problematic case)
                        if (n.getArguments().isEmpty()) {
                            // Replace with new Yaml(new LoaderOptions())
                            var loaderOptionsExpr = new ObjectCreationExpr(
                                Optional.empty(), 
                                new com.github.javaparser.ast.type.ClassOrInterfaceType("LoaderOptions"), 
                                new com.github.javaparser.ast.NodeList<>()
                            );
                            
                            n.setArguments(new com.github.javaparser.ast.NodeList<>(loaderOptionsExpr));
                            
                            System.out.println("Fixed Yaml() constructor to Yaml(new LoaderOptions())");
                        }
                    }
                }
            }
        }
    }
}