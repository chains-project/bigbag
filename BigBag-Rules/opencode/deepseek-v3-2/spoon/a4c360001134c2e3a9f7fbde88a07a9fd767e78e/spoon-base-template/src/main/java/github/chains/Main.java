package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.visitor.filter.TypeFilter;

import java.io.File;
import java.util.List;

/**
 * Generic Spoon transformation for fixing the reflections library breaking API change
 * from version < 0.10.2 to 0.10.2.
 * 
 * Breaking Change: FilterBuilder.apply(String) method was removed and replaced
 * with FilterBuilder.test(String) method (from Predicate<String> interface).
 * 
 * Old API pattern: FilterBuilder.apply(String)
 * New API pattern: FilterBuilder.test(String)
 * 
 * This transformation is generic and can be applied to any Java project
 * affected by this breaking change.
 */
public class Main {
    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Usage: java -jar spoon-transformation.jar <source-directory>");
            System.err.println("Example: java -jar spoon-transformation.jar /path/to/project/src/main/java");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        File dir = new File(sourceDir);
        if (!dir.exists() || !dir.isDirectory()) {
            System.err.println("Error: Source directory does not exist: " + sourceDir);
            System.exit(1);
        }
        
        System.out.println("Applying FilterBuilder.apply() -> test() transformation to: " + sourceDir);
        System.out.println("Breaking API Change: org.reflections.util.FilterBuilder.apply(String) -> test(String)");
        
        try {
            Launcher launcher = new Launcher();
            // Use no classpath mode to avoid compilation errors with old method signatures
            launcher.getEnvironment().setNoClasspath(true);
            launcher.getEnvironment().setAutoImports(true);
            launcher.getEnvironment().setCommentEnabled(true);
            
            // Add the source directory
            launcher.addInputResource(sourceDir);
            
            // Build the model
            CtModel model = launcher.buildModel();
            
            int replacements = 0;
            
            // Find all method invocations in the model
            List<CtInvocation<?>> invocations = model.getElements(new TypeFilter<>(CtInvocation.class));
            
            for (CtInvocation<?> invocation : invocations) {
                // Check if this is a call to FilterBuilder.apply(String)
                if (isFilterBuilderApplyCall(invocation)) {
                    String fileName = "unknown";
                    int lineNumber = -1;
                    if (invocation.getPosition().isValidPosition()) {
                        fileName = invocation.getPosition().getFile().getName();
                        lineNumber = invocation.getPosition().getLine();
                    }
                    
                    System.out.println("  - Found at: " + fileName + ":" + lineNumber);
                    
                    // Replace the method name from "apply" to "test"
                    invocation.getExecutable().setSimpleName("test");
                    replacements++;
                }
            }
            
            if (replacements > 0) {
                // Write the transformed code back
                launcher.setSourceOutputDirectory(dir.getParentFile().getAbsolutePath());
                launcher.prettyprint();
                
                System.out.println("\nTransformation complete!");
                System.out.println("Successfully replaced " + replacements + " FilterBuilder.apply() call(s) with test()");
                System.out.println("\nAPI Change Summary:");
                System.out.println("  - Old: FilterBuilder.apply(String)");
                System.out.println("  - New: FilterBuilder.test(String)");
                System.out.println("  - Reason: FilterBuilder now implements Predicate<String> interface");
            } else {
                System.out.println("\nNo FilterBuilder.apply() calls found to transform.");
                System.out.println("Either the project is already updated, or doesn't use this API.");
            }
            
        } catch (Exception e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    /**
     * Detects if an invocation is a call to FilterBuilder.apply(String)
     * 
     * @param invocation The method invocation to check
     * @return true if this is a FilterBuilder.apply() call, false otherwise
     */
    private static boolean isFilterBuilderApplyCall(CtInvocation<?> invocation) {
        try {
            // Check if method name is "apply"
            String methodName = invocation.getExecutable().getSimpleName();
            if (!"apply".equals(methodName)) {
                return false;
            }
            
            // In no-classpath mode, we check patterns in the source code
            String invocationStr = invocation.toString();
            
            // Pattern 1: Direct FilterBuilder.apply() call
            if (invocationStr.contains("FilterBuilder.apply(")) {
                return true;
            }
            
            // Pattern 2: Fully qualified org.reflections.util.FilterBuilder.apply() call
            if (invocationStr.contains("org.reflections.util.FilterBuilder.apply(")) {
                return true;
            }
            
            // Pattern 3: Check if target contains FilterBuilder
            if (invocation.getTarget() != null) {
                String targetStr = invocation.getTarget().toString();
                if (targetStr.contains("FilterBuilder") || 
                    targetStr.contains("new FilterBuilder()")) {
                    return true;
                }
            }
            
            // Pattern 4: Check for common usage patterns
            // Common pattern: new FilterBuilder().include(regex).apply(input)
            if (invocationStr.contains(".include(") && invocationStr.contains(".apply(")) {
                // Look backwards in parent chain for FilterBuilder
                return checkForFilterBuilderInContext(invocation);
            }
            
        } catch (Exception e) {
            // If any error occurs during checking, skip this invocation
            System.err.println("Warning: Error checking invocation: " + e.getMessage());
        }
        
        return false;
    }
    
    /**
     * Checks if the invocation is in the context of a FilterBuilder chain
     * by looking at parent expressions
     */
    private static boolean checkForFilterBuilderInContext(CtInvocation<?> invocation) {
        try {
            // Check parent expression for FilterBuilder pattern
            String parentStr = invocation.getParent().toString();
            return parentStr.contains("FilterBuilder") || 
                   parentStr.contains("new FilterBuilder()");
        } catch (Exception e) {
            return false;
        }
    }
}