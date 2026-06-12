package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.github.javaparser.ast.NodeList;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Main {
    public static void main(String[] args) throws IOException {
        // Process all Java files in the source directory
        String sourceDirectory = "/workspace/polyglot-maven";
        if (args.length > 0) {
            sourceDirectory = args[0];
        }
        
        processDirectory(Paths.get(sourceDirectory));
    }
    
    private static void processDirectory(Path directory) throws IOException {
        Files.walk(directory)
                .filter(path -> path.toString().endsWith(".java"))
                .forEach(Main::processFile);
    }
    
    private static void processFile(Path filePath) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(filePath.toFile());
            new YamlConstructorFixer().visit(cu, null);
            try (FileWriter writer = new FileWriter(filePath.toFile())) {
                writer.write(cu.toString());
            }
        } catch (Exception e) {
            System.err.println("Error processing " + filePath + ": " + e.getMessage());
        }
    }
    
    private static class YamlConstructorFixer extends VoidVisitorAdapter<Void> {
        @Override
        public void visit(ObjectCreationExpr n, Void arg) {
            // Check if this is a Yaml constructor call with 4 arguments that needs to be fixed
            if (n.getType() instanceof ClassOrInterfaceType) {
                ClassOrInterfaceType type = (ClassOrInterfaceType) n.getType();
                if ("Yaml".equals(type.getNameAsString()) && n.getArguments().size() == 4) {
                    // Pattern: new Yaml(constructor, representer, dumperOptions, resolver)
                    // Should become: new Yaml(constructor, representer, dumperOptions, resolver, null)
                    fixYamlConstructor(n);
                }
            }
            super.visit(n, arg);
        }
        
        private void fixYamlConstructor(ObjectCreationExpr n) {
            // Add a null argument to make it 5 arguments
            NodeList<Expression> args = n.getArguments();
            if (args.size() == 4) {
                args.add(new NullLiteralExpr());
            }
        }
    }
}