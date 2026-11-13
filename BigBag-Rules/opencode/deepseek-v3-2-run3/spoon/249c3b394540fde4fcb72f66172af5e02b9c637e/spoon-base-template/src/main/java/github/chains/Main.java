package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtType;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.util.HashSet;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar spoon-transformation.jar <source-directory> [<classpath>]");
            System.err.println("Example: java -jar spoon-transformation.jar /path/to/src /path/to/dependency1.jar:/path/to/dependency2.jar");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        String classpath = args.length > 1 ? args[1] : "";
        
        System.out.println("Processing source directory: " + sourceDir);
        if (!classpath.isEmpty()) {
            System.out.println("Using classpath: " + classpath);
        }
        
        // Create Spoon launcher
        Launcher launcher = new Launcher();
        
        // Configure environment
        launcher.getEnvironment().setNoClasspath(false);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(true);
        
        // Add source directory
        launcher.addInputResource(sourceDir);
        
        // Add classpath if provided
        if (!classpath.isEmpty()) {
            launcher.getEnvironment().setSourceClasspath(classpath.split(":"));
        }
        
        try {
            // Build the model
            CtModel model = launcher.buildModel();
            
            System.out.println("Model built successfully. Analyzing...");
            
            // Find and fix all snakeyaml-related IntrospectionException issues
            int fixes = fixSnakeYamlIntrospectionExceptionIssues(model);
            
            if (fixes > 0) {
                // Write transformed code back
                launcher.setSourceOutputDirectory(sourceDir);
                launcher.prettyprint();
                
                System.out.println("Transformation completed successfully! Fixed " + fixes + " method(s).");
            } else {
                System.out.println("No fixes needed.");
            }
            
        } catch (Exception e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static int fixSnakeYamlIntrospectionExceptionIssues(CtModel model) {
        int fixCount = 0;
        
        // Find all classes in the model
        List<CtClass<?>> allClasses = model.getElements(new TypeFilter<CtClass<?>>(CtClass.class));
        
        for (CtClass<?> clazz : allClasses) {
            // Check if this class extends any class from org.yaml.snakeyaml package
            if (extendsSnakeYamlClass(clazz)) {
                fixCount += fixIntrospectionExceptionInClass(clazz);
            }
        }
        
        return fixCount;
    }
    
    private static boolean extendsSnakeYamlClass(CtClass<?> clazz) {
        // Check superclass
        if (clazz.getSuperclass() != null) {
            String superClassName = clazz.getSuperclass().getQualifiedName();
            if (superClassName != null && superClassName.startsWith("org.yaml.snakeyaml.")) {
                return true;
            }
        }
        
        // Check implemented interfaces
        for (CtTypeReference<?> interfaceRef : clazz.getSuperInterfaces()) {
            String interfaceName = interfaceRef.getQualifiedName();
            if (interfaceName != null && interfaceName.startsWith("org.yaml.snakeyaml.")) {
                return true;
            }
        }
        
        return false;
    }
    
    private static int fixIntrospectionExceptionInClass(CtClass<?> clazz) {
        int fixCount = 0;
        
        // Check all methods in the class
        for (CtMethod<?> method : clazz.getMethods()) {
            // Check if method throws IntrospectionException
            boolean hasIntrospectionException = false;
            Set<CtTypeReference<? extends Throwable>> thrownTypes = method.getThrownTypes();
            if (thrownTypes != null) {
                for (CtTypeReference<? extends Throwable> thrownType : thrownTypes) {
                    if ("java.beans.IntrospectionException".equals(thrownType.getQualifiedName())) {
                        hasIntrospectionException = true;
                        break;
                    }
                }
            }
            
            if (hasIntrospectionException) {
                System.out.println("Found method " + method.getSignature() + " in " + 
                                 clazz.getQualifiedName() + " with throws IntrospectionException");
                
                // Remove IntrospectionException from throws clause
                Set<CtTypeReference<? extends Throwable>> newThrownTypes = new HashSet<>();
                for (CtTypeReference<? extends Throwable> thrownType : thrownTypes) {
                    if (!"java.beans.IntrospectionException".equals(thrownType.getQualifiedName())) {
                        newThrownTypes.add(thrownType);
                    }
                }
                
                if (newThrownTypes.isEmpty()) {
                    method.setThrownTypes((Set) null);
                } else {
                    method.setThrownTypes(newThrownTypes);
                }
                
                fixCount++;
                System.out.println("  -> Removed IntrospectionException from throws clause");
            }
        }
        
        return fixCount;
    }
}