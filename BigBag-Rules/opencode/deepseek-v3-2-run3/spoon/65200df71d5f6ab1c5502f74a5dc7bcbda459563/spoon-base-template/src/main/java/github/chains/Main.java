package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtType;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.processing.AbstractProcessor;
import java.io.File;
import java.util.List;

/**
 * A generic Spoon transformation to fix breaking API changes in sonarlint-core.
 * This transformation removes calls to the deprecated/removed method:
 * AnalysisEngineConfiguration.Builder.addEnabledLanguages(Set<Language>)
 * 
 * The transformation is parameterized and can be applied to any project
 * by changing the source directory path.
 */
public class Main {
    
    // Configuration parameters - should be passed as arguments or properties
    // These are examples and should be parameterized for real usage
    private static String targetClass = "org.sonarsource.sonarlint.core.analysis.api.AnalysisEngineConfiguration$Builder";
    private static String targetMethod = "addEnabledLanguages";
    private static String targetMethodSignature = "addEnabledLanguages(java.util.Set<org.sonarsource.sonarlint.core.commons.Language>)";
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar spoon-transformation.jar <source-directory> [options]");
            System.err.println("Options:");
            System.err.println("  --target-class <class>     Fully qualified class name (default: " + targetClass + ")");
            System.err.println("  --target-method <method>   Method name to remove (default: " + targetMethod + ")");
            System.err.println("  --target-sig <signature>   Method signature pattern (optional)");
            System.err.println("Example: java -jar spoon-transformation.jar /path/to/project/src/main/java");
            System.err.println("Example with params: java -jar spoon-transformation.jar /path/to/src --target-class com.example.Foo$Builder --target-method deprecatedMethod");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        
        // Parse additional arguments
        for (int i = 1; i < args.length; i++) {
            switch (args[i]) {
                case "--target-class":
                    if (i + 1 < args.length) {
                        targetClass = args[++i];
                    }
                    break;
                case "--target-method":
                    if (i + 1 < args.length) {
                        targetMethod = args[++i];
                    }
                    break;
                case "--target-sig":
                    if (i + 1 < args.length) {
                        targetMethodSignature = args[++i];
                    }
                    break;
            }
        }
        
        System.out.println("Applying transformation to: " + sourceDir);
        System.out.println("Configuration:");
        System.out.println("  Target class: " + targetClass);
        System.out.println("  Target method: " + targetMethod);
        if (targetMethodSignature != null) {
            System.out.println("  Method signature: " + targetMethodSignature);
        }
        
        try {
            applyTransformation(sourceDir);
            System.out.println("Transformation completed successfully!");
        } catch (Exception e) {
            System.err.println("Error applying transformation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static void applyTransformation(String sourceDir) {
        // Create Spoon launcher
        Launcher launcher = new Launcher();
        
        // Configure launcher
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setComplianceLevel(11);
        
        // Add input source directory
        launcher.addInputResource(sourceDir);
        
        // Build the model
        CtModel model = launcher.buildModel();
        
        // Create and add the transformation
        launcher.addProcessor(new RemoveDeprecatedMethodCallProcessor());
        
        // Apply transformations
        System.out.println("Processing source files...");
        launcher.process();
        
        // Write transformed code back
        System.out.println("Writing transformed code...");
        launcher.setSourceOutputDirectory(sourceDir);
        launcher.prettyprint();
        
        // Print summary
        System.out.println("Summary:");
        System.out.println("- Target class: " + targetClass);
        System.out.println("- Target method: " + targetMethod);
        if (targetMethodSignature != null) {
            System.out.println("- Method signature: " + targetMethodSignature);
        }
        System.out.println("- Source directory: " + sourceDir);
    }
    
    /**
     * Spoon processor that removes calls to deprecated/removed methods.
     * This processor searches for method invocations matching the target pattern
     * and removes them from the AST.
     */
    private static class RemoveDeprecatedMethodCallProcessor extends AbstractProcessor<CtInvocation<?>> {
        
        private int removedCount = 0;
        
        @Override
        public void process(CtInvocation<?> invocation) {
            try {
                // Check if this is the target method invocation
                if (isTargetMethodInvocation(invocation)) {
                    System.out.println("Found target method call at: " + 
                                     invocation.getPosition().getFile().getName() + ":" + 
                                     invocation.getPosition().getLine());
                    
                    // For builder patterns, we need to handle the chain carefully
                    // Get the parent statement and check if it's part of a builder chain
                    if (invocation.getParent() instanceof spoon.reflect.code.CtInvocation) {
                        // This is part of a chain, just delete this invocation
                        invocation.delete();
                    } else {
                        // This might be a standalone invocation or part of a different structure
                        // Try to delete it anyway
                        invocation.delete();
                    }
                    
                    removedCount++;
                    
                    System.out.println("Removed deprecated method call. Total removed: " + removedCount);
                }
            } catch (Exception e) {
                System.err.println("Error processing invocation: " + e.getMessage());
                // Continue with other invocations
            }
        }
        
        private boolean isTargetMethodInvocation(CtInvocation<?> invocation) {
            // Get the target method
            String methodName = invocation.getExecutable().getSimpleName();
            
            // Check if method name matches
            if (!targetMethod.equals(methodName)) {
                return false;
            }
            
            // Try to get the declaring type
            try {
                String declaringType = invocation.getExecutable().getDeclaringType() != null ?
                                      invocation.getExecutable().getDeclaringType().getQualifiedName() :
                                      "";
                
                // Check if declaring type matches (allowing for inner class syntax)
                if (declaringType.contains(targetClass.replace("$", "."))) {
                    return true;
                }
                
                // Also check without the Builder suffix in case of different class naming
                if (targetClass.contains("$Builder") && 
                    declaringType.contains(targetClass.replace("$Builder", "").replace("$", "."))) {
                    return true;
                }
                
            } catch (Exception e) {
                // If we can't determine the declaring type, use a more lenient check
                // Look for the method call in a chain that likely belongs to the target class
                String invocationString = invocation.toString();
                if (invocationString.contains("addEnabledLanguages") && 
                    invocationString.contains("AnalysisEngineConfiguration")) {
                    return true;
                }
            }
            
            return false;
        }
    }
    
    /**
     * Alternative approach using type filter for more precise matching.
     * This method is kept for reference but not used in the main transformation.
     */
    private static void alternativeTransformationApproach(CtModel model) {
        // Find all method invocations in the codebase
        List<CtInvocation<?>> invocations = model.getElements(new TypeFilter<CtInvocation<?>>(CtInvocation.class) {
            @Override
            public boolean matches(CtInvocation<?> invocation) {
                // Check method name
                if (!"addEnabledLanguages".equals(invocation.getExecutable().getSimpleName())) {
                    return false;
                }
                
                // Check if it's likely the target method based on context
                // This is a more lenient check that doesn't require exact type resolution
                String invocationCode = invocation.toString();
                return invocationCode.contains("AnalysisEngineConfiguration") &&
                       invocationCode.contains("addEnabledLanguages") &&
                       invocationCode.contains("Set<Language>");
            }
        });
        
        System.out.println("Found " + invocations.size() + " invocations to remove");
        
        // Remove each invocation
        for (CtInvocation<?> invocation : invocations) {
            try {
                invocation.delete();
                System.out.println("Removed invocation at: " + 
                                 invocation.getPosition().getFile().getName() + ":" + 
                                 invocation.getPosition().getLine());
            } catch (Exception e) {
                System.err.println("Failed to remove invocation: " + e.getMessage());
            }
        }
    }
}