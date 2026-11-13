package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Main {
    
    public static void main(String[] args) throws IOException {
        // This is a generic transformation for MapStruct @Mapper annotations
        // to fix breaking changes in MapStruct 1.5.0.Final
        // Specifically addresses issues with component model configuration
        
        if (args.length < 1) {
            System.err.println("Usage: java Main <source_directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        Path dir = Paths.get(sourceDir);
        
        if (!Files.exists(dir)) {
            System.err.println("Source directory does not exist: " + sourceDir);
            System.exit(1);
        }
        
        // Process all Java files in the directory
        Files.walk(dir)
            .filter(path -> path.toString().endsWith(".java"))
            .forEach(Main::processJavaFile);
    }
    
    private static void processJavaFile(Path filePath) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(new FileInputStream(filePath.toFile()));
            
            // Apply transformation to fix MapStruct @Mapper annotations
            MapStructMapperFixer transformer = new MapStructMapperFixer();
            cu.accept(transformer, null);
            
            // Save the modified file
            try (FileWriter writer = new FileWriter(filePath.toFile())) {
                writer.write(cu.toString());
            }
            System.out.println("Processed: " + filePath);
        } catch (Exception e) {
            System.err.println("Error processing " + filePath + ": " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Visitor that adds explicit component model configuration to @Mapper annotations
     * to fix breaking changes in MapStruct 1.5.0.Final
     */
    private static class MapStructMapperFixer extends ModifierVisitor<Void> {
        
        @Override
        public Visitable visit(TypeDeclaration<?> n, Void arg) {
            // Look for @Mapper annotations on the type declaration
            NodeList<AnnotationExpr> annotations = n.getAnnotations();
            
            for (int i = 0; i < annotations.size(); i++) {
                AnnotationExpr annotation = annotations.get(i);
                if (annotation instanceof MarkerAnnotationExpr) {
                    MarkerAnnotationExpr markerAnnotation = (MarkerAnnotationExpr) annotation;
                    if ("Mapper".equals(markerAnnotation.getNameAsString())) {
                        // Convert to NormalAnnotationExpr with componentModel attribute
                        NodeList<MemberValuePair> pairs = new NodeList<>();
                        
                        // Add componentModel attribute for Spring integration
                        MemberValuePair componentModelPair = new MemberValuePair("componentModel", 
                            new NameExpr("org.mapstruct.ComponentModel.SPRING"));
                        pairs.add(componentModelPair);
                        
                        NormalAnnotationExpr newAnnotation = new NormalAnnotationExpr(
                            markerAnnotation.getName(), 
                            pairs
                        );
                        
                        // Replace the original annotation
                        annotations.set(i, newAnnotation);
                    }
                } 
                // Handle NormalAnnotationExpr case for @Mapper with existing attributes
                else if (annotation instanceof NormalAnnotationExpr) {
                    NormalAnnotationExpr normalAnnotation = (NormalAnnotationExpr) annotation;
                    if ("Mapper".equals(normalAnnotation.getNameAsString())) {
                        // Check if it already has componentModel attribute
                        boolean hasComponentModel = false;
                        for (MemberValuePair pair : normalAnnotation.getPairs()) {
                            if ("componentModel".equals(pair.getNameAsString())) {
                                hasComponentModel = true;
                                break;
                            }
                        }
                        
                        // If it doesn't have componentModel, add it
                        if (!hasComponentModel) {
                            MemberValuePair componentModelPair = new MemberValuePair("componentModel", 
                                new NameExpr("org.mapstruct.ComponentModel.SPRING"));
                            normalAnnotation.getPairs().add(componentModelPair);
                        }
                    }
                }
            }
            
            return super.visit(n, arg);
        }
    }
}
