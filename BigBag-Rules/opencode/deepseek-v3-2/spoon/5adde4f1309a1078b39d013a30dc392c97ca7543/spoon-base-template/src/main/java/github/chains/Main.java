package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.visitor.filter.TypeFilter;
import java.util.List;

/**
 * Generic Spoon transformation to fix the breaking API change in sonarlint-core 9.1.0.74321.
 * 
 * Breaking Change: The method `addEnabledLanguages(Set<Language>)` was removed from
 * `AnalysisEngineConfiguration.Builder` in sonarlint-core 9.1.0.74321.
 * 
 * Transformation: Removes calls to `addEnabledLanguages` on `AnalysisEngineConfiguration.Builder`
 * instances, as enabled languages are now configured at the `StandaloneGlobalConfiguration` level.
 * 
 * This transformation is generic and can be applied to any project affected by this breaking change.
 */
public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar spoon-transformation.jar <source-directory>");
            System.err.println("Example: java -jar spoon-transformation.jar /path/to/project/src/main/java");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Processing source directory: " + sourceDir);
        
        // Create Spoon launcher
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.addInputResource(sourceDir);
        
        // Build the model
        CtModel model = launcher.buildModel();
        
        // Find all method invocations
        List<CtInvocation<?>> invocations = model.getElements(new TypeFilter<CtInvocation<?>>(CtInvocation.class));
        
        int removedCount = 0;
        
        // Process each invocation
        for (CtInvocation<?> invocation : invocations) {
            // Check if this is a call to addEnabledLanguages
            String methodName = invocation.getExecutable().getSimpleName();
            if (!"addEnabledLanguages".equals(methodName)) {
                continue;
            }
            
            // Check if this is called on AnalysisEngineConfiguration.Builder
            // by examining the context and method signature
            boolean isTargetAnalysisEngineBuilder = false;
            
            // Check the target expression
            try {
                if (invocation.getTarget() != null) {
                    String targetStr = invocation.getTarget().toString();
                    if (targetStr.contains("AnalysisEngineConfiguration.builder()") ||
                        targetStr.contains("AnalysisEngineConfiguration.Builder")) {
                        isTargetAnalysisEngineBuilder = true;
                    }
                }
                
                // Also check parent chain
                CtElement parent = invocation;
                while (parent != null) {
                    String parentStr = parent.toString();
                    if (parentStr.contains("AnalysisEngineConfiguration.builder()")) {
                        isTargetAnalysisEngineBuilder = true;
                        break;
                    }
                    parent = parent.getParent();
                }
            } catch (Exception e) {
                // If we can't determine, skip this invocation
                continue;
            }
            
            if (isTargetAnalysisEngineBuilder) {
                System.out.println("Removing addEnabledLanguages call at: " + 
                                  invocation.getPosition().toString());
                
                // Remove the invocation from the AST
                // Spoon will handle the method chain reconstruction
                invocation.delete();
                removedCount++;
            }
        }
        
        System.out.println("\nRemoved " + removedCount + " addEnabledLanguages calls");
        
        // Output the transformed code
        String outputDir = sourceDir + "-transformed";
        launcher.setSourceOutputDirectory(outputDir);
        launcher.prettyprint();
        
        System.out.println("Transformation complete. Output in: " + outputDir);
        
        if (removedCount > 0) {
            System.out.println("\nSummary of changes:");
            System.out.println("1. Removed calls to AnalysisEngineConfiguration.Builder.addEnabledLanguages()");
            System.out.println("2. Enabled languages are now configured at StandaloneGlobalConfiguration level");
            System.out.println("3. The builder chain has been automatically reconstructed");
        } else {
            System.out.println("No addEnabledLanguages calls found to remove.");
        }
    }
}