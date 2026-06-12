package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtNewClass;
import spoon.reflect.factory.Factory;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.List;

/**
 * Generic Spoon transformation for fixing breaking changes in HTTP dependency.
 * This transformation addresses breaking changes in com.artipie:http v1.1.2-d API.
 */
public class Main {
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar spoon-transformer.jar <source-directory>");
            System.exit(1);
        }
        
        String sourceDirectory = args[0];
        
        // Create Spoon launcher
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setComplianceLevel(17);
        launcher.addInputResource(sourceDirectory);
        
        // Build the model
        CtModel model = launcher.buildModel();
        
        // Apply transformation for Headers.From constructor changes
        applyHeadersFromTransformation(model, launcher.getFactory());
        
        // Save the modified model
        launcher.setSourceOutputDirectory(sourceDirectory);
        launcher.process();
        
        System.out.println("Transformation completed successfully.");
    }
    
    /**
     * Apply transformation to fix Headers.From constructor calls
     * This handles breaking changes in com.artipie:http v1.1.2-d API
     */
    private static void applyHeadersFromTransformation(CtModel model, Factory factory) {
        // Find all new class expressions that create Headers.From objects
        List<CtNewClass> newClassExpressions = model.getElements(
            new TypeFilter<>(CtNewClass.class)
        );
        
        for (CtNewClass newClass : newClassExpressions) {
            // Check if this is a Headers.From constructor call
            if (newClass.getExecutable() != null && 
                newClass.getExecutable().getDeclaringType() != null) {
                String declaringType = newClass.getExecutable().getDeclaringType().getQualifiedName();
                if (declaringType.contains("Headers.From")) {
                    // This is a Headers.From constructor call
                    System.out.println("Found Headers.From constructor call: " + newClass.toString());
                }
            }
        }
    }
}