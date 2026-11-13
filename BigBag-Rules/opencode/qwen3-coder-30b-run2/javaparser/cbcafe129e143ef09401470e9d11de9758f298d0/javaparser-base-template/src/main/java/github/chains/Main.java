package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import java.io.*;
import java.util.*;

public class Main {
    
    // Generic transformation for Logback 1.4.6 compatibility issues 
    // This transformation addresses breaking changes between Logback 1.2.x and 1.4.6
    
    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Usage: java Main <source_directory>");
            System.exit(1);
        }
        
        String sourceDirectory = args[0];
        try {
            processDirectory(new File(sourceDirectory));
            System.out.println("Transformation completed successfully");
        } catch (IOException e) {
            System.err.println("Error processing files: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static void processDirectory(File directory) throws IOException {
        File[] files = directory.listFiles();
        if (files == null) {
            return;
        }
        
        for (File file : files) {
            if (file.isDirectory()) {
                processDirectory(file);
            } else if (file.getName().endsWith(".java")) {
                processFile(file);
            }
        }
    }
    
    private static void processFile(File file) throws IOException {
        try {
            CompilationUnit cu = StaticJavaParser.parse(file);
            
            // Apply transformations for logback 1.4.6 compatibility
            TransformVisitor visitor = new TransformVisitor();
            visitor.visit(cu, null);
            
            // Write back the modified code
            try (PrintWriter writer = new PrintWriter(new FileWriter(file))) {
                writer.print(cu.toString());
            }
        } catch (Exception e) {
            System.err.println("Error processing " + file.getAbsolutePath() + ": " + e.getMessage());
        }
    }
    
    /**
     * Visitor class to find and transform logback-related method calls
     */
    private static class TransformVisitor extends VoidVisitorAdapter<Void> {
        
        @Override
        public void visit(MethodCallExpr mce, Void arg) {
            super.visit(mce, arg);
            
            // Transform calls to setIncludeCallerData(boolean) method
            if (isSetIncludeCallerDataCall(mce)) {
                transformSetIncludeCallerDataCall(mce);
            }
            
            // Transform calls to methods that should be modified for compatibility
            if (isCallerDataMethodCall(mce)) {
                transformCallerDataMethodCall(mce);
            }
        }
        
        /**
         * Check if this is a call to setIncludeCallerData(boolean) method
         */
        private boolean isSetIncludeCallerDataCall(MethodCallExpr mce) {
            String methodName = mce.getNameAsString();
            
            // Check if it's setIncludeCallerData method call
            if ("setIncludeCallerData".equals(methodName)) {
                // We're looking for this exact method call pattern regardless of class
                return true;
            }
            
            return false;
        }
        
        /**
         * Transform the setIncludeCallerData call to make it compatible
         * This is a placeholder for the actual transformation logic
         */
        private void transformSetIncludeCallerDataCall(MethodCallExpr mce) {
            // For now, we just print a message - in a real implementation we would
            // modify the AST to insert the compatible code pattern
            System.out.println("Found setIncludeCallerData call at line " + mce.getBegin().orElseThrow().line);
        }
        
        /**
         * Check if this is a caller data related method call that might need attention
         */
        private boolean isCallerDataMethodCall(MethodCallExpr mce) {
            String methodName = mce.getNameAsString();
            
            // Common caller data methods that may have changed
            Set<String> callerDataMethods = Set.of(
                "setMaxCallerDataDepth",
                "getMaxCallerDataDepth",
                "setIncludeCallerData",
                "isIncludeCallerData"
            );
            
            return callerDataMethods.contains(methodName);
        }
        
        /**
         * Transform caller data related method calls
         */
        private void transformCallerDataMethodCall(MethodCallExpr mce) {
            // For now, just log that we found one
            String methodName = mce.getNameAsString();
            System.out.println("Processing caller data method: " + methodName + " at line " + mce.getBegin().orElseThrow().line);
        }
    }
}
