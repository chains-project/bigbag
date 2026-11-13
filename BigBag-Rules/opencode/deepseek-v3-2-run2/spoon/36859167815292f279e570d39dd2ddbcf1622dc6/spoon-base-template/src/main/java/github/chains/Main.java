package github.chains;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.*;
import spoon.reflect.declaration.*;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.*;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.support.Level;

import java.io.File;
import java.net.URI;
import java.util.*;

/**
 * Generic transformation rule for fixing breaking API changes where:
 * - Old API: ClassName.methodName() with no arguments
 * - New API: ClassName.methodName(ArgumentType) requires one argument
 * 
 * This processor transforms zero-argument static method calls to single-argument
 * calls by adding a default argument (e.g., new URI(".") for FopFactory.newInstance()).
 */
public class Main {
    
    /**
     * Generic processor for transforming zero-argument static method calls
     * to single-argument calls with a configurable default argument.
     */
    public static class ZeroArgToSingleArgProcessor extends AbstractProcessor<CtInvocation<?>> {
        
        private final String targetType;
        private final String methodName;
        private final String argumentType;
        private final String argumentValue;
        private final boolean isConstructorCall;
        
        /**
         * Creates a processor for transforming zero-argument method calls.
         * 
         * @param targetType Fully-qualified class name (e.g., "org.apache.fop.apps.FopFactory")
         * @param methodName Method name (e.g., "newInstance")
         * @param argumentType Fully-qualified argument type (e.g., "java.net.URI")
         * @param argumentValue Default argument value (e.g., ".")
         * @param isConstructorCall Whether the argument is a constructor call (true) or literal (false)
         */
        public ZeroArgToSingleArgProcessor(
                String targetType, 
                String methodName, 
                String argumentType,
                String argumentValue,
                boolean isConstructorCall) {
            this.targetType = targetType;
            this.methodName = methodName;
            this.argumentType = argumentType;
            this.argumentValue = argumentValue;
            this.isConstructorCall = isConstructorCall;
        }
        
        @Override
        public boolean isToBeProcessed(CtInvocation<?> candidate) {
            // Quick filter to avoid unnecessary processing
            if (candidate.getTarget() != null) {
                return false; // Not a static method call
            }
            
            CtExecutableReference<?> execRef = candidate.getExecutable();
            if (execRef == null || !methodName.equals(execRef.getSimpleName())) {
                return false;
            }
            
            CtTypeReference<?> declaringType = execRef.getDeclaringType();
            if (declaringType == null || !targetType.equals(declaringType.getQualifiedName())) {
                return false;
            }
            
            // Only process zero-argument calls
            return candidate.getArguments().isEmpty();
        }
        
        @Override
        public void process(CtInvocation<?> invocation) {
            try {
                // Create the argument expression
                CtExpression<?> argument = createArgumentExpression();
                
                // Add the argument to the invocation
                invocation.addArgument(argument);
                
                // Log the transformation
                getFactory().getEnvironment().report(
                    this, 
                    Level.INFO, 
                    "Transformed " + targetType + "." + methodName + "() -> " + 
                    targetType + "." + methodName + "(" + argumentType + ") at " + 
                    invocation.getPosition()
                );
                
            } catch (Exception e) {
                getFactory().getEnvironment().report(
                    this, 
                    Level.ERROR, 
                    "Failed to transform " + targetType + "." + methodName + "() at " + 
                    invocation.getPosition() + ": " + e.getMessage()
                );
            }
        }
        
        private CtExpression<?> createArgumentExpression() {
            Factory factory = getFactory();
            
            if (isConstructorCall) {
                // Create a constructor call (e.g., new URI("."))
                try {
                    Class<?> argClass = Class.forName(argumentType);
                    CtTypeReference<?> argTypeRef = factory.Type().createReference(argClass);
                    CtConstructorCall<?> constructorCall = factory.Code().createConstructorCall(argTypeRef);
                    
                    // Add the argument value to the constructor
                    if (argumentValue != null && !argumentValue.isEmpty()) {
                        CtLiteral<String> valueLiteral = factory.Code().createLiteral(argumentValue);
                        constructorCall.addArgument(valueLiteral);
                    }
                    
                    return constructorCall;
                    
                } catch (ClassNotFoundException e) {
                    // If class not found, try to create reference by name
                    CtTypeReference<?> argTypeRef = factory.Type().createReference(argumentType);
                    CtConstructorCall<?> constructorCall = factory.Code().createConstructorCall(argTypeRef);
                    
                    if (argumentValue != null && !argumentValue.isEmpty()) {
                        CtLiteral<String> valueLiteral = factory.Code().createLiteral(argumentValue);
                        constructorCall.addArgument(valueLiteral);
                    }
                    
                    return constructorCall;
                }
            } else {
                // Create a literal value
                // Parse the value based on its type
                if ("int".equals(argumentType) || "java.lang.Integer".equals(argumentType)) {
                    return factory.Code().createLiteral(Integer.parseInt(argumentValue));
                } else if ("long".equals(argumentType) || "java.lang.Long".equals(argumentType)) {
                    return factory.Code().createLiteral(Long.parseLong(argumentValue));
                } else if ("boolean".equals(argumentType) || "java.lang.Boolean".equals(argumentType)) {
                    return factory.Code().createLiteral(Boolean.parseBoolean(argumentValue));
                } else if ("double".equals(argumentType) || "java.lang.Double".equals(argumentType)) {
                    return factory.Code().createLiteral(Double.parseDouble(argumentValue));
                } else if ("float".equals(argumentType) || "java.lang.Float".equals(argumentType)) {
                    return factory.Code().createLiteral(Float.parseFloat(argumentValue));
                } else {
                    // Default to string literal
                    return factory.Code().createLiteral(argumentValue);
                }
            }
        }
    }
    
    /**
     * Creates a processor for the specific FopFactory.newInstance() breaking change.
     * In FOP 2.2, FopFactory.newInstance() with no arguments was removed.
     * It's replaced by FopFactory.newInstance(java.net.URI) among other variants.
     */
    private static ZeroArgToSingleArgProcessor createFopFactoryProcessor() {
        return new ZeroArgToSingleArgProcessor(
            "org.apache.fop.apps.FopFactory",  // Target class
            "newInstance",                     // Method name
            "java.net.URI",                    // Argument type (URI)
            ".",                               // Default argument value (current directory)
            true                               // Argument is a constructor call
        );
    }
    
    /**
     * Example of another processor configuration for a different API change.
     * This shows how the transformation can be extended to other breaking changes.
     */
    private static ZeroArgToSingleArgProcessor createExampleProcessor() {
        return new ZeroArgToSingleArgProcessor(
            "com.example.SomeClass",           // Target class
            "someMethod",                      // Method name  
            "java.lang.String",                // Argument type
            "defaultValue",                    // Default argument value
            false                              // Argument is a literal, not constructor
        );
    }
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Generic Spoon Transformation Rule for Breaking API Changes");
            System.err.println("==========================================================");
            System.err.println("");
            System.err.println("Usage: java -cp <classpath> github.chains.Main <source-directory>");
            System.err.println("");
            System.err.println("Description:");
            System.err.println("  Transforms zero-argument static method calls to single-argument");
            System.err.println("  calls with a configurable default argument.");
            System.err.println("");
            System.err.println("Example (for FopFactory.newInstance() breaking change):");
            System.err.println("  java -cp . github.chains.Main /path/to/project/src");
            System.err.println("");
            System.err.println("Transformation applied:");
            System.err.println("  Old: FopFactory.newInstance()");
            System.err.println("  New: FopFactory.newInstance(new URI(\".\"))");
            System.err.println("");
            System.err.println("Configuration:");
            System.err.println("  Edit the createFopFactoryProcessor() method to customize");
            System.err.println("  target class, method name, argument type and value.");
            return;
        }
        
        String sourceDir = args[0];
        File sourceDirFile = new File(sourceDir);
        
        if (!sourceDirFile.exists() || !sourceDirFile.isDirectory()) {
            System.err.println("Error: Source directory does not exist or is not a directory: " + sourceDir);
            return;
        }
        
        System.out.println("Processing source directory: " + sourceDir);
        System.out.println("Applying transformation for FopFactory.newInstance() -> FopFactory.newInstance(new URI(\".\"))");
        
        Launcher launcher = new Launcher();
        
        // Configure launcher
        launcher.addInputResource(sourceDir);
        launcher.getEnvironment().setNoClasspath(false);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(true);
        launcher.getEnvironment().setLevel("INFO");
        
        // Create and add processor for FopFactory.newInstance()
        ZeroArgToSingleArgProcessor processor = createFopFactoryProcessor();
        launcher.addProcessor(processor);
        
        try {
            // Run the transformation
            launcher.run();
            System.out.println("Transformation completed successfully.");
            System.out.println("");
            System.out.println("Summary:");
            System.out.println("  - Target: " + processor.targetType + "." + processor.methodName + "()");
            System.out.println("  - Replacement: " + processor.targetType + "." + processor.methodName + "(new " + 
                               processor.argumentType + "(\"" + processor.argumentValue + "\"))");
            
        } catch (Exception e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}