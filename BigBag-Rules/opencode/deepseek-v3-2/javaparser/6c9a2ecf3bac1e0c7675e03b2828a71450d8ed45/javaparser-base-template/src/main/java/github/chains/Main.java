package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.body.*;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.github.javaparser.printer.DefaultPrettyPrinter;

import java.io.File;
import java.io.FileNotFoundException;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java github.chains.Main <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Transforming MapStruct annotations in: " + sourceDir);
        
        try {
            transformMapStructAnnotations(sourceDir);
            System.out.println("Transformation completed successfully");
        } catch (Exception e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static void transformMapStructAnnotations(String sourceDir) throws Exception {
        Path startDir = Paths.get(sourceDir);
        if (!Files.exists(startDir) || !Files.isDirectory(startDir)) {
            throw new IllegalArgumentException("Source directory does not exist: " + sourceDir);
        }
        
        List<Path> javaFiles = Files.walk(startDir)
            .filter(p -> p.toString().endsWith(".java"))
            .collect(Collectors.toList());
        
        System.out.println("Found " + javaFiles.size() + " Java files to process");
        
        JavaParser parser = new JavaParser();
        int transformedFiles = 0;
        
        for (Path javaFile : javaFiles) {
            try {
                CompilationUnit cu = parser.parse(javaFile).getResult().orElse(null);
                if (cu == null) {
                    continue;
                }
                
                boolean modified = false;
                MapStructAnnotationVisitor visitor = new MapStructAnnotationVisitor();
                visitor.visit(cu, null);
                
                if (visitor.isModified()) {
                    DefaultPrettyPrinter printer = new DefaultPrettyPrinter();
                    String transformedCode = printer.print(cu);
                    Files.write(javaFile, transformedCode.getBytes());
                    transformedFiles++;
                    System.out.println("Transformed: " + javaFile);
                }
            } catch (Exception e) {
                System.err.println("Error processing " + javaFile + ": " + e.getMessage());
            }
        }
        
        System.out.println("Total files transformed: " + transformedFiles);
    }
    
    private static class MapStructAnnotationVisitor extends VoidVisitorAdapter<Void> {
        private boolean modified = false;
        
        public boolean isModified() {
            return modified;
        }
        
        @Override
        public void visit(ClassOrInterfaceDeclaration n, Void arg) {
            super.visit(n, arg);
            
            NodeList<AnnotationExpr> annotations = n.getAnnotations();
            for (AnnotationExpr annotation : annotations) {
                if (isMapStructMapperAnnotation(annotation)) {
                    ModifiedAnnotationResult result = updateMapStructAnnotationIfNeeded(annotation);
                    if (result.modified) {
                        modified = true;
                    }
                }
            }
        }
        
        private boolean isMapStructMapperAnnotation(AnnotationExpr annotation) {
            String annotationName = annotation.getNameAsString();
            
            if (annotationName.equals("Mapper")) {
                return true;
            }
            
            if (annotationName.contains(".")) {
                String simpleName = annotationName.substring(annotationName.lastIndexOf('.') + 1);
                if (simpleName.equals("Mapper")) {
                    return true;
                }
            }
            
            return false;
        }
        
        private ModifiedAnnotationResult updateMapStructAnnotationIfNeeded(AnnotationExpr annotation) {
            if (!(annotation instanceof NormalAnnotationExpr) && !(annotation instanceof SingleMemberAnnotationExpr)) {
                NormalAnnotationExpr newAnnotation = new NormalAnnotationExpr(
                    annotation.getName(),
                    new NodeList<>()
                );
                
                annotation.replace(newAnnotation);
                return new ModifiedAnnotationResult(true, newAnnotation);
            }
            
            return new ModifiedAnnotationResult(false, annotation);
        }
    }
    
    private static class ModifiedAnnotationResult {
        final boolean modified;
        final AnnotationExpr annotation;
        
        ModifiedAnnotationResult(boolean modified, AnnotationExpr annotation) {
            this.modified = modified;
            this.annotation = annotation;
        }
    }
}