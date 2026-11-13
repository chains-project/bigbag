package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.MemberValuePair;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java github.chains.Main <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Processing source directory: " + sourceDir);
        
        try {
            Path start = Paths.get(sourceDir);
            try (Stream<Path> stream = Files.walk(start)) {
                stream.filter(path -> path.toString().endsWith(".java"))
                      .forEach(Main::processJavaFile);
            }
            System.out.println("Transformation complete!");
        } catch (Exception e) {
            System.err.println("Error processing files: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static void processJavaFile(Path javaFilePath) {
        try {
            File file = javaFilePath.toFile();
            JavaParser parser = new JavaParser();
            CompilationUnit cu = parser.parse(file).getResult().orElseThrow(
                () -> new RuntimeException("Failed to parse: " + file)
            );
            
            boolean[] modified = {false};
            
            cu.accept(new ModifierVisitor<Void>() {
                @Override
                public Visitable visit(NormalAnnotationExpr n, Void arg) {
                    if (n.getNameAsString().equals("Mapper") || 
                        n.getNameAsString().endsWith(".Mapper")) {
                        // Check if componentModel attribute already exists
                        boolean hasComponentModel = n.getPairs().stream()
                            .anyMatch(pair -> pair.getNameAsString().equals("componentModel"));
                        
                        if (!hasComponentModel) {
                            // Add componentModel = "spring" 
                            n.addPair("componentModel", new StringLiteralExpr("spring"));
                            modified[0] = true;
                            System.out.println("Updated @Mapper annotation in: " + javaFilePath);
                        }
                    }
                    return super.visit(n, arg);
                }
                
                @Override
                public Visitable visit(com.github.javaparser.ast.expr.MarkerAnnotationExpr n, Void arg) {
                    if (n.getNameAsString().equals("Mapper") || 
                        n.getNameAsString().endsWith(".Mapper")) {
                        // Convert marker annotation to normal annotation with componentModel
                        NormalAnnotationExpr newAnnotation = new NormalAnnotationExpr(
                            n.getName(),
                            new com.github.javaparser.ast.NodeList<>()
                        );
                        newAnnotation.addPair("componentModel", new StringLiteralExpr("spring"));
                        modified[0] = true;
                        System.out.println("Converted @Mapper to @Mapper(componentModel = \"spring\") in: " + javaFilePath);
                        return newAnnotation;
                    }
                    return super.visit(n, arg);
                }
            }, null);
            
            if (modified[0]) {
                try (FileOutputStream fos = new FileOutputStream(file)) {
                    fos.write(cu.toString().getBytes());
                }
            }
            
        } catch (Exception e) {
            System.err.println("Error processing " + javaFilePath + ": " + e.getMessage());
        }
    }
}