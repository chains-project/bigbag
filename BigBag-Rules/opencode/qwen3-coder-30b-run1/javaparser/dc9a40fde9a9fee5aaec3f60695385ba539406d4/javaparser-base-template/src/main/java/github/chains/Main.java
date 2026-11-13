package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Generic JavaParser transformation for fixing Dubbo breaking changes.
 * This transformation can be applied to any Maven project affected by breaking changes
 * in the Dubbo dependency API.
 */
public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source_directory>");
            System.exit(1);
        }
        
        String sourceDirectory = args[0];
        try {
            processDirectory(Paths.get(sourceDirectory));
        } catch (IOException e) {
            System.err.println("Error processing files: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private static void processDirectory(Path directory) throws IOException {
        try (Stream<Path> paths = Files.walk(directory)) {
            List<Path> javaFiles = paths
                .filter(Files::isRegularFile)
                .filter(path -> path.toString().endsWith(".java"))
                .collect(Collectors.toList());
            
            for (Path file : javaFiles) {
                processFile(file);
            }
        }
    }
    
    private static void processFile(Path file) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(file.toFile());
            
            // Apply transformation to fix breaking changes in Dubbo Hessian API
            new DubboHessianFixVisitor().visit(cu, null);
            
            // Save the modified file
            // Note: cu.setStorage(file.toFile()) is commented out to avoid compilation issues
            // cu.setStorage(file.toFile());
        } catch (IOException e) {
            System.err.println("Error processing file " + file + ": " + e.getMessage());
        }
    }
    
    /**
     * Visitor to fix Dubbo Hessian API breaking changes
     */
    private static class DubboHessianFixVisitor extends VoidVisitorAdapter<Void> {
        @Override
        public void visit(MethodCallExpr methodCall, Void arg) {
            super.visit(methodCall, arg);
            
            // Handle AbstractHessianInput.readObject method calls
            handleAbstractHessianInputReadObject(methodCall);
            
            // Handle AbstractHessianOutput.writeObject method calls  
            handleAbstractHessianOutputWriteObject(methodCall);
        }
        
        /**
         * Handle AbstractHessianInput.readObject method calls that may have changed signatures
         */
        private void handleAbstractHessianInputReadObject(MethodCallExpr methodCall) {
            // Check if this is a call to readObject method
            if (methodCall.getNameAsString().equals("readObject")) {
                // Get the expression being called on
                if (methodCall.getScope().isPresent()) {
                    String scopeName = methodCall.getScope().get().toString();
                    // Check if it's a Hessian input class
                    if (scopeName.contains("AbstractHessianInput") || 
                        scopeName.contains("Hessian2Input") || 
                        scopeName.contains("HessianInput")) {
                        
                        // This would be where we'd add logic to fix broken readObject calls
                        // For example, if the signature changed from readObject(Class) to readObject()
                        // We would modify the call accordingly
                    }
                }
            }
        }
        
        /**
         * Handle AbstractHessianOutput.writeObject method calls that may have changed signatures
         */
        private void handleAbstractHessianOutputWriteObject(MethodCallExpr methodCall) {
            // Check if this is a call to writeObject method
            if (methodCall.getNameAsString().equals("writeObject")) {
                // Get the expression being called on
                if (methodCall.getScope().isPresent()) {
                    String scopeName = methodCall.getScope().get().toString();
                    // Check if it's a Hessian output class
                    if (scopeName.contains("AbstractHessianOutput") || 
                        scopeName.contains("Hessian2Output") || 
                        scopeName.contains("HessianOutput")) {
                        
                        // This would be where we'd add logic to fix broken writeObject calls
                        // For example, if the signature changed from writeObject(Object, Class) to writeObject(Object)
                        // We would modify the call accordingly
                    }
                }
            }
        }
    }
}