package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import java.io.File;
import java.io.FileNotFoundException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java github.chains.Main <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Transforming source files in: " + sourceDir);
        
        try {
            transformProject(sourceDir);
            System.out.println("Transformation completed successfully.");
        } catch (Exception e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static void transformProject(String sourceDir) throws Exception {
        List<File> javaFiles = findAllJavaFiles(new File(sourceDir));
        System.out.println("Found " + javaFiles.size() + " Java files to process.");
        
        int transformedFiles = 0;
        int totalRemovedCalls = 0;
        
        for (File javaFile : javaFiles) {
            TransformResult result = transformFile(javaFile);
            if (result.transformed) {
                transformedFiles++;
                totalRemovedCalls += result.removedCount;
            }
        }
        
        System.out.println("Transformed " + transformedFiles + " files, removed " + totalRemovedCalls + " enableLogging() calls.");
    }
    
    private static List<File> findAllJavaFiles(File directory) {
        List<File> javaFiles = new ArrayList<>();
        if (directory.isDirectory()) {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        javaFiles.addAll(findAllJavaFiles(file));
                    } else if (file.getName().endsWith(".java")) {
                        javaFiles.add(file);
                    }
                }
            }
        }
        return javaFiles;
    }
    
    private static TransformResult transformFile(File javaFile) throws FileNotFoundException {
        JavaParser parser = new JavaParser();
        CompilationUnit cu = parser.parse(javaFile).getResult().orElseThrow(() -> 
            new RuntimeException("Failed to parse file: " + javaFile.getPath()));
        
        EnableLoggingRemover remover = new EnableLoggingRemover();
        // The visitor modifies the AST in place
        remover.visit(cu, null);
        
        if (remover.getRemovedCount() > 0) {
            System.out.println("  Transforming: " + javaFile.getPath());
            System.out.println("    Removed " + remover.getRemovedCount() + " enableLogging() calls");
            
            try {
                Files.write(Paths.get(javaFile.getAbsolutePath()), cu.toString().getBytes());
                return new TransformResult(true, remover.getRemovedCount());
            } catch (Exception e) {
                System.err.println("    Error writing file: " + e.getMessage());
            }
        }
        
        return new TransformResult(false, 0);
    }
    
    private static class TransformResult {
        boolean transformed;
        int removedCount;
        
        TransformResult(boolean transformed, int removedCount) {
            this.transformed = transformed;
            this.removedCount = removedCount;
        }
    }
    
    private static class EnableLoggingRemover extends ModifierVisitor<Void> {
        private int removedCount = 0;
        
        @Override
        public Visitable visit(ExpressionStmt n, Void arg) {
            // Check if this expression statement contains an enableLogging call
            if (n.getExpression() instanceof MethodCallExpr) {
                MethodCallExpr call = (MethodCallExpr) n.getExpression();
                if ("enableLogging".equals(call.getNameAsString())) {
                    // Remove the entire expression statement
                    removedCount++;
                    System.out.println("    Removing enableLogging call: " + call.toString());
                    return null;
                }
            }
            
            // Continue with normal visitation
            return super.visit(n, arg);
        }
        
        public int getRemovedCount() {
            return removedCount;
        }
    }
}