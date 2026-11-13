package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.*;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.CtScanner;
import java.util.List;
import java.util.ArrayList;

/**
 * Generic transformation to fix Jakarta Servlet API 6.0.0 breaking changes:
 * - Removes imports of jakarta.servlet.http.HttpSessionContext
 * - Removes getSessionContext() method implementations in classes implementing HttpSession
 * 
 * This transformation can be applied to any project affected by the removal of
 * HttpSessionContext in Jakarta Servlet API 6.0.0.
 * 
 * Usage: java github.chains.Main <source-directory> [output-directory]
 * If output-directory is not specified, transformed files are written to source-directory-transformed
 */
public class Main {
    
    private static int removedImportCount = 0;
    private static int removedMethodCount = 0;
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java github.chains.Main <source-directory> [output-directory]");
            System.err.println("  source-directory: Root directory of Java source code to transform");
            System.err.println("  output-directory: Optional output directory (default: source-directory-transformed)");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        String outputDir = args.length > 1 ? args[1] : sourceDir + "-transformed";
        
        System.out.println("Transforming source directory: " + sourceDir);
        System.out.println("Output directory: " + outputDir);
        
        try {
            // Create Spoon launcher
            Launcher launcher = new Launcher();
            launcher.addInputResource(sourceDir);
            launcher.getEnvironment().setNoClasspath(true);
            launcher.getEnvironment().setAutoImports(true);
            launcher.getEnvironment().setCommentEnabled(true);
            
            // Set output directory
            launcher.setSourceOutputDirectory(outputDir);
            
            // Build model
            System.out.println("Building model...");
            CtModel model = launcher.buildModel();
            
            // Create and apply transformation
            System.out.println("Applying transformation...");
            HttpSessionContextRemover transformer = new HttpSessionContextRemover();
            model.getRootPackage().accept(transformer);
            
            // Write transformed code back
            System.out.println("Writing transformed code...");
            launcher.prettyprint();
            
            System.out.println("Transformation completed successfully.");
            System.out.println("Output written to: " + outputDir);
            System.out.println("Removed " + removedImportCount + " HttpSessionContext imports.");
            System.out.println("Removed " + removedMethodCount + " getSessionContext() method implementations.");
            
        } catch (Exception e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    /**
     * Visitor that removes HttpSessionContext imports and getSessionContext() methods.
     */
    private static class HttpSessionContextRemover extends CtScanner {
        
        @Override
        public void visitCtImport(CtImport ctImport) {
            super.visitCtImport(ctImport);
            
            // Check if this import is for jakarta.servlet.http.HttpSessionContext
            String importString = ctImport.toString();
            if (importString.contains("jakarta.servlet.http.HttpSessionContext")) {
                // Remove the import
                ctImport.delete();
                removedImportCount++;
                System.out.println("Removed import: " + importString);
            }
        }
        
        @Override
        public <T> void visitCtClass(CtClass<T> ctClass) {
            super.visitCtClass(ctClass);
            
            // Check if this class implements jakarta.servlet.http.HttpSession
            boolean implementsHttpSession = false;
            for (CtTypeReference<?> interfaceRef : ctClass.getSuperInterfaces()) {
                if (interfaceRef.getQualifiedName().equals("jakarta.servlet.http.HttpSession")) {
                    implementsHttpSession = true;
                    break;
                }
            }
            
            if (implementsHttpSession) {
                // Find and remove getSessionContext() method
                List<CtMethod<?>> methodsToRemove = new ArrayList<>();
                for (CtMethod<?> method : ctClass.getMethods()) {
                    if ("getSessionContext".equals(method.getSimpleName())) {
                        // Check if it has the correct signature (no parameters)
                        if (method.getParameters().isEmpty()) {
                            methodsToRemove.add(method);
                        }
                    }
                }
                
                // Remove the methods
                for (CtMethod<?> method : methodsToRemove) {
                    method.delete();
                    removedMethodCount++;
                    System.out.println("Removed getSessionContext() method from class: " + ctClass.getQualifiedName());
                }
            }
        }
        
        @Override
        public <T> void visitCtInterface(CtInterface<T> ctInterface) {
            super.visitCtInterface(ctInterface);
            
            // Also check interfaces that might extend HttpSession
            boolean extendsHttpSession = false;
            for (CtTypeReference<?> interfaceRef : ctInterface.getSuperInterfaces()) {
                if (interfaceRef.getQualifiedName().equals("jakarta.servlet.http.HttpSession")) {
                    extendsHttpSession = true;
                    break;
                }
            }
            
            if (extendsHttpSession) {
                // Find and remove getSessionContext() method declaration
                List<CtMethod<?>> methodsToRemove = new ArrayList<>();
                for (CtMethod<?> method : ctInterface.getMethods()) {
                    if ("getSessionContext".equals(method.getSimpleName())) {
                        // Check if it has the correct signature (no parameters)
                        if (method.getParameters().isEmpty()) {
                            methodsToRemove.add(method);
                        }
                    }
                }
                
                // Remove the methods
                for (CtMethod<?> method : methodsToRemove) {
                    method.delete();
                    removedMethodCount++;
                    System.out.println("Removed getSessionContext() method declaration from interface: " + ctInterface.getQualifiedName());
                }
            }
        }
    }
}