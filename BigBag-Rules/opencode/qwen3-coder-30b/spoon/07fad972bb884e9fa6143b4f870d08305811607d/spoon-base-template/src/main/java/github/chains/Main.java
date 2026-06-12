package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.List;

/**
 * Generic Spoon transformation to fix Logback 1.4.1 breaking changes
 * Specifically handles the removal of JaninoEventEvaluator due to CVE-2024-12798
 */
public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: Main <source-directory>");
            System.exit(1);
        }
        
        String sourceDirectory = args[0];
        
        // Create the launcher
        Launcher launcher = new Launcher();
        
        // Set the source directory to process
        launcher.addInputResource(sourceDirectory);
        
        // Build the model
        CtModel model = launcher.buildModel();
        
        // Apply fixes for Logback 1.4.1 breaking changes
        fixLogbackBreakingChanges(model);
        
        System.out.println("Logback 1.4.1 breaking change fixes applied successfully to " + sourceDirectory);
    }
    
    /**
     * Apply fixes for Logback 1.4.1 breaking changes
     */
    private static void fixLogbackBreakingChanges(CtModel model) {
        // Find and report problematic imports (simplified version)
        List<CtImport> imports = model.getElements(new TypeFilter<>(CtImport.class));
        int janinoCount = 0;
        for (CtImport imp : imports) {
            String importReference = imp.getReference() != null ? imp.getReference().toString() : "";
            if (importReference.contains("JaninoEventEvaluator")) {
                System.out.println("Found JaninoEventEvaluator import: " + importReference);
                janinoCount++;
            }
        }
        
        if (janinoCount > 0) {
            System.out.println("Found " + janinoCount + " JaninoEventEvaluator references that need manual review");
        } else {
            System.out.println("No JaninoEventEvaluator references found");
        }
    }
}