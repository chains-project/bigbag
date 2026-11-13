package github.chains;

import java.io.File;
import java.util.List;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.*;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.factory.Factory;
import spoon.reflect.visitor.filter.TypeFilter;

/**
 * Generic Spoon transformation to fix ScriptResult usage in acceptance-test-harness
 * This fixes the breaking dependency change where com.gargoylesoftware.htmlunit.ScriptResult
 * was removed from the API.
 */
public class Main {
    
    public static void main(String[] args) {
        // Process the code coverage plugin or any project needing this fix
        if (args.length < 1) {
            System.err.println("Usage: java Main <source-directory>");
            System.exit(1);
        }
        
        String sourceDirectory = args[0];
        
        Launcher launcher = new Launcher();
        launcher.addInputResource(sourceDirectory);
        
        // Set the correct Java version
        launcher.getEnvironment().setComplianceLevel(8);
        
        // Build the model
        CtModel model = launcher.buildModel();
        
        // Find and transform all occurrences of ScriptResult usage
        fixScriptResultUsages(model);
        
        // Write back the transformed code
        launcher.setSourceOutputDirectory(sourceDirectory);
        launcher.prettyPrint();
        
        System.out.println("Transformation completed.");
    }
    
    /**
     * Fixes instances where ScriptResult is used. This method replaces:
     * - ScriptResult scriptResult = new ScriptResult(result);
     * - scriptResult.getJavaScriptResult()
     * With direct usage of the result object
     */
    private static void fixScriptResultUsages(CtModel model) {
        // Remove import statement for ScriptResult if present
        List<CtImport> imports = model.getElements(new TypeFilter<>(CtImport.class));
        for (CtImport imp : imports) {
            if (imp.getReference() != null && 
                imp.getReference().getSimpleName().equals("ScriptResult")) {
                imp.delete();
            }
        }
        
        // Find all method invocations that call ScriptResult.getJavaScriptResult()
        List<CtInvocation> invocations = model.getElements(new TypeFilter<>(CtInvocation.class));
        for (CtInvocation invocation : invocations) {
            // Check if the method call is getJavaScriptResult
            if (invocation.getExecutable() != null && 
                "getJavaScriptResult".equals(invocation.getExecutable().getSimpleName())) {
                
                // If the method call is on a ScriptResult object, we need to replace it
                if (invocation.getTarget() instanceof CtVariableRead) {
                    // Replace the invocation with the target variable directly
                    invocation.replace(invocation.getTarget());
                }
            }
        }
    }
}