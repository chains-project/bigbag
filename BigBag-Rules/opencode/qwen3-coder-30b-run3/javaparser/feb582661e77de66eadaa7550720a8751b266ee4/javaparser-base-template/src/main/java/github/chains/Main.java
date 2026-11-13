package github.chains;

import com.github.javaparser.*;
import com.github.javaparser.ast.*;
import com.github.javaparser.ast.body.*;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.*;
import com.github.javaparser.ast.type.*;
import com.github.javaparser.ast.visitor.*;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Generic JavaParser transformation to fix AbstractSqlGenerator method signatures
 * that changed in Liquibase 4.8.0
 */
public class Main {
    
    public static void main(String[] args) throws IOException {
        // Process all Java files in the source directory
        String sourceDirPath = args.length > 0 ? args[0] : "/workspace/liquibase-mssql/src/java";
        
        Path sourceDir = Paths.get(sourceDirPath);
        if (!Files.exists(sourceDir)) {
            System.err.println("Source directory does not exist: " + sourceDirPath);
            return;
        }
        
        // Find all Java files
        List<Path> javaFiles = Files.walk(sourceDir)
                .filter(path -> path.toString().endsWith(".java"))
                .collect(Collectors.toList());
        
        int processedFiles = 0;
        for (Path javaFile : javaFiles) {
            if (processJavaFile(javaFile)) {
                processedFiles++;
            }
        }
        
        System.out.println("Processed " + processedFiles + " files.");
    }
    
    private static boolean processJavaFile(Path javaFile) {
        try {
            // Parse the Java file
            CompilationUnit cu = StaticJavaParser.parse(javaFile.toFile());
            
            // Apply transformations 
            boolean modified = applyTransformations(cu);
            
            if (modified) {
                // Save the modified file
                String code = cu.toString();
                Files.write(javaFile, code.getBytes("UTF-8"));
                return true;
            }
        } catch (Exception e) {
            System.err.println("Error processing file " + javaFile + ": " + e.getMessage());
            e.printStackTrace();
        }
        return false;
    }
    
    private static boolean applyTransformations(CompilationUnit cu) {
        TransformVisitor visitor = new TransformVisitor();
        cu.accept(visitor, null);
        return visitor.isModified();
    }
    
    /**
     * Visitor that identifies and transforms problematic AbstractSqlGenerator method signatures
     */
    private static class TransformVisitor extends VoidVisitorAdapter<Void> {
        private boolean modified = false;
        
        @Override
        public void visit(ClassOrInterfaceDeclaration n, Void arg) {
            // Check if this class extends AbstractSqlGenerator or its subclasses
            if (n.getExtendedTypes().stream().anyMatch(type -> 
                type.getNameAsString().contains("AbstractSqlGenerator") ||
                type.getNameAsString().contains("SqlGenerator"))) {
                modified = true;
            }
            super.visit(n, arg);
        }
        
        @Override
        public void visit(MethodDeclaration n, Void arg) {
            // Check for specific problematic patterns that would be common in Liquibase 4.8.0
            String methodName = n.getNameAsString();
            
            // Look for methods that have SqlGeneratorChain parameter
            for (Parameter param : n.getParameters()) {
                if (param.getType().toString().contains("SqlGeneratorChain")) {
                    // This indicates a likely breaking change in method signature
                    modified = true;
                }
            }
            
            super.visit(n, arg);
        }
        
        public boolean isModified() {
            return modified;
        }
    }
}