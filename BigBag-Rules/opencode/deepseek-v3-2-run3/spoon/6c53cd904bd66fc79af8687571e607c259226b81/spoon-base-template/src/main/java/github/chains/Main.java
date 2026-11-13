package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.*;
import spoon.reflect.reference.*;
import spoon.reflect.visitor.Filter;
import spoon.reflect.code.*;

import spoon.reflect.factory.Factory;

import java.util.*;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

/**
 * Generic Spoon transformation for fixing breaking API changes in dependencies.
 * 
 * This transformer handles changes in method signatures when a dependency updates
 * and client code needs to be updated to match the new API.
 * 
 * Configuration parameters:
 * - oldMethodSignature: Fully qualified method signature in old API
 * - newMethodSignature: Fully qualified method signature in new API  
 * - sourceDirectory: Source code directory to transform
 * - outputDirectory: Output directory for transformed code
 * 
 * Example usage for struts2 ObjectFactory.buildInterceptor change:
 * - oldMethodSignature: com.opensymphony.xwork2.ObjectFactory#buildInterceptor(com.opensymphony.xwork2.config.entities.InterceptorConfig,java.util.Map)
 * - newMethodSignature: com.opensymphony.xwork2.ObjectFactory#buildInterceptor(com.opensymphony.xwork2.config.entities.InterceptorConfig,java.util.Map<java.lang.String,java.lang.String>)
 */
public class Main {
    
    private static class TransformationConfig {
        String oldMethodSignature;
        String newMethodSignature;
        String sourceDir;
        String outputDir;
        
        // Parse signature into components
        String oldClassName;
        String oldMethodName;
        List<String> oldParamTypes = new ArrayList<>();
        
        String newClassName;
        String newMethodName;
        List<String> newParamTypes = new ArrayList<>();
        
        void parseSignatures() {
            parseSignature(oldMethodSignature, true);
            parseSignature(newMethodSignature, false);
        }
        
        private void parseSignature(String signature, boolean isOld) {
            // Format: com.example.Class#methodName(paramType1,paramType2)
            Pattern pattern = Pattern.compile("([^#]+)#([^\\(]+)\\(([^)]*)\\)");
            Matcher matcher = pattern.matcher(signature);
            
            if (matcher.matches()) {
                if (isOld) {
                    oldClassName = matcher.group(1);
                    oldMethodName = matcher.group(2);
                    parseParamTypes(matcher.group(3), oldParamTypes);
                } else {
                    newClassName = matcher.group(1);
                    newMethodName = matcher.group(2);
                    parseParamTypes(matcher.group(3), newParamTypes);
                }
            } else {
                throw new IllegalArgumentException("Invalid signature format: " + signature);
            }
        }
        
        private void parseParamTypes(String paramString, List<String> paramTypes) {
            if (paramString == null || paramString.trim().isEmpty()) {
                return;
            }
            
            // Split by comma, but be careful with nested generics
            StringBuilder current = new StringBuilder();
            int angleBracketDepth = 0;
            
            for (char c : paramString.toCharArray()) {
                if (c == '<') {
                    angleBracketDepth++;
                    current.append(c);
                } else if (c == '>') {
                    angleBracketDepth--;
                    current.append(c);
                } else if (c == ',' && angleBracketDepth == 0) {
                    paramTypes.add(current.toString().trim());
                    current = new StringBuilder();
                } else {
                    current.append(c);
                }
            }
            
            if (current.length() > 0) {
                paramTypes.add(current.toString().trim());
            }
        }
    }
    
    public static void main(String[] args) {
        if (args.length < 4) {
            System.err.println("Usage: java -jar spoon-transformer.jar <oldMethodSignature> <newMethodSignature> <sourceDir> <outputDir>");
            System.err.println("");
            System.err.println("Example for struts2 ObjectFactory.buildInterceptor change:");
            System.err.println("  java -jar spoon-transformer.jar \\");
            System.err.println("    'com.opensymphony.xwork2.ObjectFactory#buildInterceptor(com.opensymphony.xwork2.config.entities.InterceptorConfig,java.util.Map)' \\");
            System.err.println("    'com.opensymphony.xwork2.ObjectFactory#buildInterceptor(com.opensymphony.xwork2.config.entities.InterceptorConfig,java.util.Map<java.lang.String,java.lang.String>)' \\");
            System.err.println("    /path/to/source \\");
            System.err.println("    /path/to/output");
            System.err.println("");
            System.err.println("This would transform code calling/overriding the old signature to use the new signature.");
            System.exit(1);
        }
        
        TransformationConfig config = new TransformationConfig();
        config.oldMethodSignature = args[0];
        config.newMethodSignature = args[1];
        config.sourceDir = args[2];
        config.outputDir = args[3];
        
        try {
            config.parseSignatures();
            
            System.out.println("=== API Breaking Change Transformation ===");
            System.out.println("Old signature: " + config.oldMethodSignature);
            System.out.println("  - Class: " + config.oldClassName);
            System.out.println("  - Method: " + config.oldMethodName);
            System.out.println("  - Parameters: " + config.oldParamTypes);
            System.out.println();
            System.out.println("New signature: " + config.newMethodSignature);
            System.out.println("  - Class: " + config.newClassName);
            System.out.println("  - Method: " + config.newMethodName);
            System.out.println("  - Parameters: " + config.newParamTypes);
            System.out.println();
            System.out.println("Source directory: " + config.sourceDir);
            System.out.println("Output directory: " + config.outputDir);
            System.out.println("===========================================");
            
            // Check if classes are the same (should be for method signature changes)
            if (!config.oldClassName.equals(config.newClassName)) {
                System.err.println("Warning: Class names differ between old and new signatures.");
                System.err.println("This transformation handles method signature changes within the same class.");
            }
            
            if (!config.oldMethodName.equals(config.newMethodName)) {
                System.err.println("Warning: Method names differ between old and new signatures.");
                System.err.println("This transformation handles parameter changes, not method name changes.");
            }
            
            // Run the transformation
            runTransformation(config);
            
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
        
        return;
    }
    
    private static void runTransformation(TransformationConfig config) {
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.addInputResource(config.sourceDir);
        launcher.setSourceOutputDirectory(config.outputDir);
        
        System.out.println("Building AST model...");
        CtModel model = launcher.buildModel();
        
        System.out.println("Applying transformations...");
        
        // Count transformations applied
        int transformationCount = 0;
        
        // Visit all classes in the model
        for (CtType<?> type : model.getAllTypes()) {
            if (type instanceof CtClass) {
                transformationCount += transformClass((CtClass<?>) type, config);
            }
        }
        
        System.out.println("Writing transformed code...");
        launcher.prettyprint();
        
        System.out.println("\n=== Transformation Summary ===");
        System.out.println("Total transformations applied: " + transformationCount);
        System.out.println("Output written to: " + config.outputDir);
        
        if (transformationCount == 0) {
            System.out.println("\nWarning: No transformations were applied.");
            System.out.println("Check that:");
            System.out.println("1. The source directory contains Java files");
            System.out.println("2. The method signatures match exactly");
            System.out.println("3. The code actually uses the old API pattern");
        }
    }
    
    private static int transformClass(CtClass<?> ctClass, TransformationConfig config) {
        int transformations = 0;
        
        // Check if this class extends or implements the affected class
        boolean extendsAffectedClass = false;
        
        // Check superclass
        CtTypeReference<?> superClass = ctClass.getSuperclass();
        if (superClass != null && config.oldClassName.equals(superClass.getQualifiedName())) {
            extendsAffectedClass = true;
        }
        
        // Check implemented interfaces
        for (CtTypeReference<?> iface : ctClass.getSuperInterfaces()) {
            if (config.oldClassName.equals(iface.getQualifiedName())) {
                extendsAffectedClass = true;
                break;
            }
        }
        
        if (extendsAffectedClass) {
            System.out.println("Found class extending/implementing " + config.oldClassName + ": " + 
                             ctClass.getQualifiedName());
            
            // Look for methods that might need updating
            for (CtMethod<?> method : ctClass.getMethods()) {
                if (method.getAnnotation(Override.class) != null) {
                    transformations += checkAndTransformMethod(method, config);
                }
            }
        }
        
        return transformations;
    }
    
    private static int checkAndTransformMethod(CtMethod<?> method, TransformationConfig config) {
        // Check if method signature matches old pattern
        // This is simplified - real implementation would need full type matching
        
        // Check method name matches
        if (!method.getSimpleName().equals(config.oldMethodName)) {
            return 0;
        }
        
        // Check parameter count matches
        if (method.getParameters().size() != config.oldParamTypes.size()) {
            return 0;
        }
        
        // Check parameter types (simplified comparison)
        boolean paramsMatch = true;
        List<CtParameter<?>> params = method.getParameters();
        for (int i = 0; i < params.size(); i++) {
            String paramType = params.get(i).getType().toString();
            String expectedType = config.oldParamTypes.get(i);
            
            // Simple type comparison - would need to handle generics better
            if (!paramType.equals(expectedType) && 
                !paramType.replaceAll("\\s+", "").equals(expectedType.replaceAll("\\s+", ""))) {
                paramsMatch = false;
                break;
            }
        }
        
        if (paramsMatch) {
            System.out.println("  Found matching method: " + method.getSignature());
            
            // Apply transformation
            return applyMethodTransformation(method, config);
        }
        return 0;
    }
    
    private static int applyMethodTransformation(CtMethod<?> method, TransformationConfig config) {
        Factory factory = method.getFactory();
        
        try {
            // Update method parameters to match new signature
            List<CtParameter<?>> newParams = new ArrayList<>();
            
            for (int i = 0; i < config.newParamTypes.size(); i++) {
                String paramTypeStr = config.newParamTypes.get(i);
                
                // Create type reference from string
                CtTypeReference<?> paramTypeRef = factory.createReference(paramTypeStr);
                
                // Create parameter (reuse existing parameter name if possible)
                String paramName = "param" + i;
                if (i < method.getParameters().size()) {
                    paramName = method.getParameters().get(i).getSimpleName();
                }
                
                CtParameter<?> param = factory.createParameter(
                    method, 
                    paramTypeRef, 
                    paramName
                );
                newParams.add(param);
            }
            
            // Replace parameters
            method.setParameters(newParams);
            
            // Add comment documenting the change
            CtComment comment = factory.createComment(
                "Method signature updated due to breaking API change in " + config.oldClassName + "\n" +
                "Original signature: " + config.oldMethodSignature + "\n" +
                "New signature: " + config.newMethodSignature + "\n" +
                "Transformed automatically by generic API migration tool.",
                CtComment.CommentType.BLOCK
            );
            method.addComment(comment);
            
            System.out.println("    -> Transformed to new signature");
            return 1;
            
        } catch (Exception e) {
            System.err.println("    -> Error transforming method: " + e.getMessage());
            return 0;
        }
    }
}