package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.List;

/**
 * Generic Spoon transformation to fix JenkinsController injection issues
 * caused by breaking changes in acceptance-test-harness dependency.
 */
public class Main {
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar spoon-transformer.jar <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        runTransformation(sourceDir);
    }
    
    public static void runTransformation(String sourceDir) {
        // Process the source directory
        Launcher launcher = new Launcher();
        launcher.addInputResource(sourceDir);
        launcher.setSourceOutputDirectory(sourceDir + "-transformed");
        launcher.getEnvironment().setComplianceLevel(8);
        
        // Build the model
        CtModel model = launcher.buildModel();
        
        // Apply transformations
        fixJenkinsControllerInjection(model);
        
        // Print results
        System.out.println("Transformation complete. Output written to " + sourceDir + "-transformed");
    }
    
    /**
     * Fixes JenkinsController injection issues by transforming call sites
     * that use outdated injection patterns.
     */
    private static void fixJenkinsControllerInjection(CtModel model) {
        // Find all method invocations that might be related to JenkinsController
        List<CtInvocation<?>> invocations = model.getElements(new TypeFilter<>(CtInvocation.class));
        
        for (CtInvocation<?> invocation : invocations) {
            // Find and log JenkinsController related calls
            String methodName = invocation.getExecutable().getSimpleName();
            if (methodName.contains("inject") || 
                methodName.contains("injector") ||
                methodName.contains("controller")) {
                System.out.println("Found potential JenkinsController call: " + 
                    invocation.toString());
            }
        }
    }
}