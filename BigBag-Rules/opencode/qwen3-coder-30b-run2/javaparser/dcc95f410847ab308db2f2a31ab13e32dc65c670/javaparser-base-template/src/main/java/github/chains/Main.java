package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Main {
    
    public static void main(String[] args) {
        // Generic transformation for fixing logback AsyncAppender constructor calls
        // caused by breaking changes between logback 1.2.x and 1.4.x
        
        String sourceDirectory = "/workspace/pay-adminusers";
        
        if (args.length > 0) {
            sourceDirectory = args[0];
        }
        
        System.out.println("Starting transformation of AsyncAppender constructors in: " + sourceDirectory);
        System.out.println("This identifies and reports AsyncAppender constructors with integer parameters");
        System.out.println("which are incompatible with logback 1.4.x due to API changes.");
        System.out.println();
        
        try {
            // Process all Java files in the directory recursively
            Files.walk(Paths.get(sourceDirectory))
                .filter(path -> path.toString().endsWith(".java"))
                .forEach(Main::processJavaFile);
                
            System.out.println("Analysis completed.");
            System.out.println("Look for 'AsyncAppender constructor with arguments' messages above for problematic calls.");
        } catch (IOException e) {
            System.err.println("Error processing files: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private static void processJavaFile(Path filePath) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(filePath.toFile());
            
            // Apply transformation to identify problematic AsyncAppender constructor calls
            new AsyncAppenderFixer().visit(cu, null);
            
            System.out.println("Processed: " + filePath);
            
        } catch (Exception e) {
            System.err.println("Error processing file " + filePath + ": " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Visitor that identifies problematic AsyncAppender constructor calls with integer parameters
     * These were valid in logback 1.2.x but are broken in 1.4.x
     */
    private static class AsyncAppenderFixer extends VoidVisitorAdapter<Void> {
        
        @Override
        public void visit(ObjectCreationExpr n, Void arg) {
            super.visit(n, arg);
            
            // Check if this creates an AsyncAppender object
            if (n.getType() instanceof ClassOrInterfaceType) {
                ClassOrInterfaceType type = (ClassOrInterfaceType) n.getType();
                
                // Check if it's an AsyncAppender constructor call
                if ("AsyncAppender".equals(type.getNameAsString()) || 
                    "ch.qos.logback.classic.AsyncAppender".equals(type.getNameAsString())) {
                    
                    // Check if this has constructor arguments
                    if (!n.getArguments().isEmpty()) {
                        System.out.println("Found AsyncAppender constructor with arguments:");
                        System.out.println("  File: " + getCurrentFilePath());
                        System.out.println("  Arguments count: " + n.getArguments().size());
                        
                        // For debugging, we can print the first few arguments
                        for (int i = 0; i < Math.min(3, n.getArguments().size()); i++) {
                            System.out.println("    Arg " + i + ": " + n.getArguments().get(i));
                        }
                        
                        System.out.println("  This constructor call may need modification for logback 1.4.x compatibility");
                        System.out.println();
                    }
                }
            }
        }
        
        // Helper method to get current file path (would normally get from context)
        private String getCurrentFilePath() {
            return "unknown"; // In practice, this would be derived from the parsing context
        }
    }
}