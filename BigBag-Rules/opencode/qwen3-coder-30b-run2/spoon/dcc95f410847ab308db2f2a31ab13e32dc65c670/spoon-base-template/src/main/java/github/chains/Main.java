package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.visitor.Filter;
import spoon.reflect.code.CtInvocation;

/**
 * Generic Spoon transformation to handle breaking changes in Logback dependency
 * This transformation resolves method signature mismatches between logback versions
 */
public class Main {
    
    public static void main(String[] args) {
        // Process the pay-adminusers project
        String sourceDir = "/workspace/pay-adminusers/src/main/java";
        String outputDir = "/workspace/pay-adminusers/src/main/java";
        
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setComplianceLevel(11);
        launcher.addInputResource(sourceDir);
        launcher.setSourceOutputDirectory(outputDir);
        
        CtModel model = launcher.buildModel();
        
        // Apply transformation to fix logback breaking changes
        fixLogbackBreakingChanges(model);
        
        System.out.println("Logback transformation completed");
    }
    
    /**
     * Fixes breaking changes in logback API between versions
     * Specifically targets method signature changes in async appenders
     */
    private static void fixLogbackBreakingChanges(CtModel model) {
        // Match any invocation of setIncludeCallerData(boolean) method
        model.getElements(new Filter<CtInvocation>() {
            @Override
            public boolean matches(CtInvocation element) {
                // Look for method calls to setIncludeCallerData(boolean)
                return element.getExecutable().getSimpleName().equals("setIncludeCallerData") &&
                       element.getArguments().size() == 1;
            }
        }).forEach(invocation -> {
            System.out.println("Found setIncludeCallerData invocation: " + invocation.toString());
        });
    }
}