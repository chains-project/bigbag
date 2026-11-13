package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.HashMap;
import java.util.Map;

/**
 * Generic Spoon transformation for fixing API breaking changes.
 * This transformation replaces deprecated/removed method calls with their new equivalents.
 * 
 * Configuration:
 * - METHOD_REPLACEMENTS map defines which methods to replace
 * - Format: "fully.qualified.ClassName.oldMethodName" -> "newMethodName"
 * 
 * Example: For XSeries 8.6.0 breaking change:
 * - Old: XEnchantment.parseEnchantment()
 * - New: XEnchantment.getEnchant()
 * 
 * Usage: java -jar spoon-transform.jar <source-directory>
 */
public class Main {
    
    // Configuration for method replacements
    // Format: "fully.qualified.ClassName.oldMethodName" -> "newMethodName"
    // Add more entries for other breaking changes
    private static final Map<String, String> METHOD_REPLACEMENTS = new HashMap<>();
    
    static {
        // XSeries 8.6.0 breaking change: XEnchantment.parseEnchantment() -> XEnchantment.getEnchant()
        METHOD_REPLACEMENTS.put("com.cryptomorin.xseries.XEnchantment.parseEnchantment", "getEnchant");
        
        // Add more breaking changes here as needed:
        // METHOD_REPLACEMENTS.put("com.example.Class.deprecatedMethod", "newMethod");
    }
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar spoon-transform.jar <source-directory>");
            System.err.println("Example: java -jar spoon-transform.jar /path/to/project/src");
            System.err.println("\nThis tool fixes API breaking changes by replacing deprecated method calls.");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Processing source directory: " + sourceDir);
        System.out.println("Looking for method replacements: " + METHOD_REPLACEMENTS);
        
        try {
            Launcher launcher = new Launcher();
            // Use noClasspath mode to handle broken code that doesn't compile
            launcher.getEnvironment().setNoClasspath(true);
            launcher.getEnvironment().setAutoImports(true);
            launcher.getEnvironment().setCommentEnabled(true);
            launcher.getEnvironment().setLevel("OFF"); // Reduce logging
            launcher.addInputResource(sourceDir);
            
            CtModel model = launcher.buildModel();
            
            System.out.println("Model built successfully");
            
            // Apply transformation
            int replacements = applyTransformations(model);
            
            System.out.println("\nApplied " + replacements + " method replacement(s)");
            
            if (replacements > 0) {
                // Write transformed code back
                launcher.setSourceOutputDirectory(sourceDir);
                launcher.prettyprint();
                System.out.println("Transformation completed successfully");
            } else {
                System.out.println("No transformations applied - check if the methods exist in your code");
            }
            
        } catch (Exception e) {
            System.err.println("Error during transformation: " + e.getMessage());
            System.err.println("\nNote: If you get compilation errors, try using the simple text replacement version.");
            System.exit(1);
        }
    }
    
    /**
     * Apply method replacement transformations to the model.
     * 
     * @param model The Spoon model of the source code
     * @return Number of replacements made
     */
    private static int applyTransformations(CtModel model) {
        final int[] replacementCount = {0};
        
        // Find all method invocations in the code
        model.getElements(new TypeFilter<CtInvocation<?>>(CtInvocation.class))
            .forEach(invocation -> {
                try {
                    CtExecutableReference<?> execRef = invocation.getExecutable();
                    if (execRef == null) return;
                    
                    String methodName = execRef.getSimpleName();
                    CtTypeReference<?> declaringType = execRef.getDeclaringType();
                    if (declaringType == null) return;
                    
                    String declaringTypeName = declaringType.getQualifiedName();
                    
                    // Check if this method needs replacement
                    for (Map.Entry<String, String> entry : METHOD_REPLACEMENTS.entrySet()) {
                        String oldMethodPattern = entry.getKey();
                        String newMethodName = entry.getValue();
                        
                        String[] parts = oldMethodPattern.split("\\.");
                        if (parts.length < 2) continue;
                        
                        // Reconstruct class name (all parts except the last)
                        StringBuilder classNameBuilder = new StringBuilder();
                        for (int i = 0; i < parts.length - 1; i++) {
                            if (i > 0) classNameBuilder.append(".");
                            classNameBuilder.append(parts[i]);
                        }
                        String targetClassName = classNameBuilder.toString();
                        String targetMethodName = parts[parts.length - 1];
                        
                        // Check if this is the method we're looking for
                        if (methodName.equals(targetMethodName) && 
                            declaringTypeName.equals(targetClassName)) {
                            
                            System.out.println("Found " + targetClassName + "." + targetMethodName + "() at " +
                                              (invocation.getPosition() != null ? 
                                               invocation.getPosition().getFile().getName() + ":" + invocation.getPosition().getLine() : 
                                               "unknown location"));
                            
                            // Create the new method reference
                            CtExecutableReference newExecRef = invocation.getFactory()
                                .createExecutableReference();
                            newExecRef.setSimpleName(newMethodName);
                            newExecRef.setDeclaringType(declaringType);
                            
                            // Copy type parameters if any
                            if (execRef.getActualTypeArguments() != null && 
                                !execRef.getActualTypeArguments().isEmpty()) {
                                newExecRef.setActualTypeArguments(execRef.getActualTypeArguments());
                            }
                            
                            // Copy parameters (important for overloaded methods)
                            if (execRef.getParameters() != null && 
                                !execRef.getParameters().isEmpty()) {
                                newExecRef.setParameters(execRef.getParameters());
                            }
                            
                            // Replace the method call
                            invocation.setExecutable(newExecRef);
                            replacementCount[0]++;
                            
                            System.out.println("  -> Replaced with " + newMethodName + "()");
                        }
                    }
                } catch (Exception e) {
                    System.err.println("Error processing invocation: " + e.getMessage());
                }
            });
        
        return replacementCount[0];
    }
}