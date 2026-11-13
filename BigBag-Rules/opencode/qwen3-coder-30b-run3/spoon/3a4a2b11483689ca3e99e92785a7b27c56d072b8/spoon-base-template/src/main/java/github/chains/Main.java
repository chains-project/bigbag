package github.chains;

import spoon.Launcher;
import spoon.SpoonModelBuilder;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtNewClass;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.reflect.reference.CtTypeReference;

import java.io.File;

/**
 * Generic Spoon transformation to fix ScriptResult usage in Jenkins acceptance test harness.
 * This transformation replaces deprecated usage of com.gargoylesoftware.htmlunit.ScriptResult
 * with direct usage of the result object from executeScript.
 */
public class Main {
    public static void main(String[] args) {
        Launcher launcher = new Launcher();
        
        // Set the source directory to transform
        String sourceDir = args.length > 0 ? args[0] : "/workspace/code-coverage-api-plugin";
        
        // Add the source directory
        launcher.addInputResource(sourceDir);
        
        // Set the output directory
        launcher.setSourceOutputDirectory(sourceDir + "/transformed");
        
        // Build the model
        CtModel model = launcher.buildModel();
        
        // Find and fix all ScriptResult new class instantiations
        model.getElements(new TypeFilter<CtNewClass>(CtNewClass.class) {
            @Override
            public boolean matches(CtNewClass element) {
                CtTypeReference<?> typeRef = element.getType();
                return typeRef != null && 
                       typeRef.getQualifiedName().equals("com.gargoylesoftware.htmlunit.ScriptResult");
            }
        }).forEach(newClass -> {
            // Find the assignment that contains this new class
            CtElement parent = newClass.getParent();
            if (parent instanceof CtExpression) {
                // Replace the entire expression (ScriptResult(...) assignment) with its argument
                if (!newClass.getArguments().isEmpty()) {
                    CtExpression<?> resultArg = newClass.getArguments().get(0);
                    parent.replace(resultArg);
                }
            }
        });
        
        // Find and fix all getJavaScriptResult() calls
        model.getElements(new TypeFilter<CtInvocation>(CtInvocation.class) {
            @Override
            public boolean matches(CtInvocation element) {
                return element.getExecutable() != null && 
                       element.getExecutable().getSimpleName().equals("getJavaScriptResult") &&
                       element.getTarget() != null;
            }
        }).forEach(invocation -> {
            // Replace getJavaScriptResult() call with just the target
            // (the ScriptResult object itself)
            invocation.replace(invocation.getTarget());
        });
        
        // Print out what was transformed
        System.out.println("Transformations completed.");
        launcher.process();
    }
}