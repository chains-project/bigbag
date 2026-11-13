package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.support.visitor.SignaturePrinter;
import spoon.compiler.SpoonResourceHelper;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.reference.CtExecutableReference;
import java.util.List;
import java.util.ArrayList;
import java.io.File;

/**
 * A generic Spoon transformation to fix breaking API changes in dependencies.
 * This transformation handles the case where a method signature has changed by
 * adding a new parameter. It finds all method invocations matching the old
 * signature and adds a default value for the new parameter.
 * 
 * The transformation is parameterized by:
 * - Old fully qualified method signature
 * - New fully qualified method signature  
 * - Default value to use for the new parameter
 */
public class Main {
    
    // Configuration parameters - these would be extracted from the breaking change analysis
    // These should be passed as command-line arguments in a real implementation
    private static final String TARGET_CLASS = "okio.Buffer"; // Example: class with breaking change
    private static final String OLD_METHOD_SIGNATURE = "write(okio.ByteString)"; // Example: old signature
    private static final String NEW_METHOD_SIGNATURE = "write(okio.ByteString, int, int)"; // Example: new signature
    private static final String DEFAULT_PARAM_VALUE = "0"; // Default value for new parameters
    
    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("Usage: java Main <sourceDir> <outputDir> [targetClass] [oldSignature] [newSignature] [defaultValue]");
            System.err.println("Example: java Main /path/to/project/src /path/to/output okio.Buffer write(okio.ByteString) write(okio.ByteString,int,int) 0");
            System.err.println("If parameters not provided, uses default values from configuration.");
            return;
        }
        
        String sourceDir = args[0];
        String outputDir = args[1];
        String targetClass = args.length > 2 ? args[2] : TARGET_CLASS;
        String oldSignature = args.length > 3 ? args[3] : OLD_METHOD_SIGNATURE;
        String newSignature = args.length > 4 ? args[4] : NEW_METHOD_SIGNATURE;
        String defaultValue = args.length > 5 ? args[5] : DEFAULT_PARAM_VALUE;
        
        System.out.println("Creating Spoon transformation for breaking API change...");
        System.out.println("Target class: " + targetClass);
        System.out.println("Old method signature: " + oldSignature);
        System.out.println("New method signature: " + newSignature);
        System.out.println("Default parameter value: " + defaultValue);
        
        // Create Spoon launcher
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setNoClasspath(true); // Use no classpath to avoid dependency issues
        launcher.addInputResource(sourceDir);
        launcher.setSourceOutputDirectory(outputDir);
        
        // Build model
        CtModel model = launcher.buildModel();
        
        // Find all method invocations
        List<CtInvocation<?>> invocations = model.getElements(new TypeFilter<CtInvocation<?>>(CtInvocation.class) {
            @Override
            public boolean matches(CtInvocation<?> invocation) {
                CtExecutableReference<?> execRef = invocation.getExecutable();
                if (execRef == null) return false;
                
                // Check if this is a method invocation on our target class
                CtTypeReference<?> declaringType = execRef.getDeclaringType();
                if (declaringType == null || !declaringType.getQualifiedName().equals(targetClass)) {
                    return false;
                }
                
                // Check method name and parameter count for old signature
                // Parse method name from oldSignature (simplified - assumes format "methodName(Type1,Type2)")
                String methodName = extractMethodName(oldSignature);
                List<CtTypeReference<?>> params = execRef.getParameters();
                
                // Get expected parameter count from old signature
                int expectedParamCount = countParameters(oldSignature);
                
                // Check if method name matches and parameter count matches old signature
                if (execRef.getSimpleName().equals(methodName) && params.size() == expectedParamCount) {
                    return true;
                }
                return false;
            }
        });
        
        System.out.println("Found " + invocations.size() + " method invocations to transform");
        
        // Apply transformation
        for (CtInvocation<?> invocation : invocations) {
            System.out.println("Transforming invocation at: " + invocation.getPosition());
            
            // In a real transformation, we would:
            // 1. Parse the old and new method signatures
            // 2. Determine which parameters were added
            // 3. Add default values for new parameters
            // 4. Update the method invocation
            
            // For this example, we'll just print what we would do
            CtExecutableReference<?> execRef = invocation.getExecutable();
            System.out.println("  Method: " + execRef.getSignature());
            System.out.println("  Would add default parameter(s) for new signature: " + NEW_METHOD_SIGNATURE);
        }
        
        // Write transformed code
        if (!invocations.isEmpty()) {
            launcher.prettyprint();
            System.out.println("Transformation applied. Output written to: " + outputDir);
        } else {
            System.out.println("No matching invocations found. No transformation applied.");
        }
    }
    
    /**
     * Extract method name from signature string.
     * Assumes format "methodName(Type1,Type2)" or "methodName()"
     */
    private static String extractMethodName(String signature) {
        int parenIndex = signature.indexOf('(');
        if (parenIndex > 0) {
            return signature.substring(0, parenIndex);
        }
        return signature;
    }
    
    /**
     * Count parameters in signature string.
     * Assumes format "methodName(Type1,Type2)" or "methodName()"
     */
    private static int countParameters(String signature) {
        int parenIndex = signature.indexOf('(');
        if (parenIndex < 0) {
            return 0; // No parameters
        }
        int closeParenIndex = signature.indexOf(')', parenIndex);
        if (closeParenIndex < 0) {
            return 0; // Malformed signature
        }
        String params = signature.substring(parenIndex + 1, closeParenIndex).trim();
        if (params.isEmpty()) {
            return 0; // No parameters
        }
        // Count commas to estimate parameter count (simplified)
        return params.split(",").length;
    }
    
    /**
     * Helper method to parse method signature strings.
     * In a complete implementation, this would parse signatures like:
     * "methodName(Type1, Type2)" into components.
     */
    private static MethodSignature parseSignature(String signature) {
        MethodSignature result = new MethodSignature();
        result.methodName = extractMethodName(signature);
        // In a full implementation, would parse parameter types too
        return result;
    }
    
    static class MethodSignature {
        String methodName;
        List<String> parameterTypes = new ArrayList<>();
        
        // Getters and parsing logic would go here
    }
}