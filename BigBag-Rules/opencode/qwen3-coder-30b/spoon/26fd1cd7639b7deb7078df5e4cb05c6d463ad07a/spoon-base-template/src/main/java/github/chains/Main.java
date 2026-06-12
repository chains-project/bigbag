package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.*;
import spoon.reflect.declaration.*;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.reflect.visitor.filter.InvocationFilter;

import java.util.List;

/**
 * Generic Spoon transformation to fix breaking API changes in acceptance-test-harness
 * Specifically handles removal of ScriptResult class usage in executeScript calls
 */
public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Usage: java Main <source_directory>");
            return;
        }
        
        String sourceDirectory = args[0];
        
        // Create Spoon factory
        Launcher launcher = new Launcher();
        launcher.addInputResource(sourceDirectory);
        launcher.setSourceOutputDirectory(sourceDirectory + "/fixed");
        launcher.getEnvironment().setComplianceLevel(8);
        
        // Build the model
        CtModel model = launcher.buildModel();
        
        // Apply transformation to fix ScriptResult usage
        fixScriptResultUsage(model);
        
        // Process the model to generate fixed files
        launcher.setSourceOutputDirectory(sourceDirectory + "/fixed");
        launcher.process();
        
        System.out.println("Transformation completed. Fixed files are in " + sourceDirectory + "/fixed");
    }
    
    /**
     * Fix usage of ScriptResult in executeScript calls
     * Replaces: new ScriptResult(result).getJavaScriptResult()
     * With: result
     */
    public static void fixScriptResultUsage(CtModel model) {
        // Find all method invocations of executeScript
        List<CtInvocation> executeScriptCalls = model.getElements(new InvocationFilter("executeScript"));
        
        for (CtInvocation invocation : executeScriptCalls) {
            // Look for the pattern where executeScript is used in a context that 
            // involves ScriptResult.getJavaScriptResult()
            CtExpression<?> parent = invocation.getParent();
            if (parent instanceof CtInvocation) {
                CtInvocation parentInvocation = (CtInvocation) parent;
                if (parentInvocation.getExecutable().getSimpleName().equals("getJavaScriptResult")) {
                    // Replace the entire expression with just the executeScript call
                    // This handles the pattern: new ScriptResult(result).getJavaScriptResult()  
                    // Should become: result (which is the executeScript call)
                    CtExpression<?> target = parentInvocation.getTarget();
                    if (target instanceof CtNewClass) {
                        CtNewClass newClass = (CtNewClass) target;
                        if (newClass.getExecutable().getDeclaringType().getQualifiedName().contains("ScriptResult")) {
                            // Replace the whole expression with the executeScript call
                            parentInvocation.replace(invocation);
                        }
                    }
                }
            }
        }
    }
}