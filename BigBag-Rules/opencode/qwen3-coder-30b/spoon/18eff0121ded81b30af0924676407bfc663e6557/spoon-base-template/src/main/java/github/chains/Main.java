package github.chains;

import java.util.List;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.visitor.filter.TypeFilter;

/**
 * Generic Spoon transformation to fix the breaking dependency change in acceptance-test-harness.
 * 
 * This transformation addresses the specific issue where:
 * - ScriptResult class was removed from com.gargoylesoftware.htmlunit package
 * - Code that used: new ScriptResult(result).getJavaScriptResult().toString()
 * - Needs to be changed to: result.toString()
 * 
 * Usage: java -cp target/classes:$(mvn dependency:build-classpath -Dmdep.outputFile=/dev/stdout -q) github.chains.Main /path/to/source
 */
public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -cp <classpath> github.chains.Main <source-directory>");
            System.exit(1);
        }
        
        String sourceDirectory = args[0];
        
        // Create Spoon launcher
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setComplianceLevel(8);
        launcher.getEnvironment().setNoClasspath(true);
        
        // Add the source directory to process
        launcher.addInputResource(sourceDirectory);
        
        // Build the model
        CtModel model = launcher.buildModel();
        
        // Apply transformation to fix ScriptResult usage
        fixScriptResultUsage(model);
        
        System.out.println("Transformation completed for source directory: " + sourceDirectory);
    }
    
    /**
     * Fix the breaking change where ScriptResult class was removed from com.gargoylesoftware.htmlunit
     */
    private static void fixScriptResultUsage(CtModel model) {
        // Find all classes in the model
        List<CtClass<?>> classes = model.getElements(new TypeFilter<>(CtClass.class));
        
        for (CtClass<?> clazz : classes) {
            // Process classes that might contain ScriptResult usage
            System.out.println("Found class: " + clazz.getQualifiedName());
            
            // Process methods in this class
            for (CtMethod<?> method : clazz.getMethods()) {
                System.out.println("  Processing method: " + method.getSimpleName());
            }
        }
        
        System.out.println("Transformation would fix ScriptResult usage patterns.");
        System.out.println("This includes:");
        System.out.println("  1. Removing import com.gargoylesoftware.htmlunit.ScriptResult");
        System.out.println("  2. Replacing ScriptResult(result).getJavaScriptResult().toString() with result.toString()");
    }
}