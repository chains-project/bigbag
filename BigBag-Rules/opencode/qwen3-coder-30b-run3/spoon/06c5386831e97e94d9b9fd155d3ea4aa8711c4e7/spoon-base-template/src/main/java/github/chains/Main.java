package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtNewClass;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.visitor.filter.TypeFilter;

/**
 * Generic Spoon transformation to handle breaking changes in API dependencies.
 * Specifically addresses cases where classes like com.gargoylesoftware.htmlunit.ScriptResult
 * are removed or changed in a breaking way.
 */
public class Main {

    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source-directory>");
            System.exit(1);
        }

        String sourceDirectory = args[0];
        
        Launcher launcher = new Launcher();
        launcher.addInputResource(sourceDirectory);
        launcher.setSourceOutputDirectory(sourceDirectory + "-transformed");
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setComplianceLevel(11);

        CtModel model = launcher.buildModel();

        // Transform the codebase
        transform(model);

        // Generate the transformed code
        launcher.process();
        
        System.out.println("Transformation completed successfully.");
    }

    /**
     * Generic transformation for breaking API changes related to ScriptResult
     * This handles the removal of com.gargoylesoftware.htmlunit.ScriptResult
     */
    private static void transform(CtModel model) {
        // Find all instantiations of ScriptResult and replace with direct assignment
        model.getElements(new TypeFilter<>(CtNewClass.class) {
            @Override
            public boolean matches(CtNewClass element) {
                return element.getType().getSimpleName().equals("ScriptResult") &&
                       element.getType().getQualifiedName().contains("com.gargoylesoftware.htmlunit");
            }
        }).forEach(scriptResultInstantiation -> {
            // Remove the ScriptResult constructor to fix compilation errors
            scriptResultInstantiation.replace(scriptResultInstantiation.getArguments().get(0));
        });
        
        // Find all usages of ScriptResult.getJavaScriptResult() and simplify
        model.getElements(new TypeFilter<>(CtInvocation.class) {
            @Override
            public boolean matches(CtInvocation element) {
                return element.getExecutable() != null && 
                       element.getExecutable().getSimpleName().equals("getJavaScriptResult") &&
                       element.getExecutable().getDeclaringType().getQualifiedName().contains("ScriptResult");
            }
        }).forEach(invocation -> {
            // Replace the invocation with the receiver object directly
            if (invocation.getTarget() != null) {
                invocation.replace(invocation.getTarget());
            }
        });
        
        // Remove imports of ScriptResult
        model.getElements(new TypeFilter<>(CtImport.class) {
            @Override
            public boolean matches(CtImport element) {
                return element.getReference() != null && 
                       element.getReference().getQualifiedName().contains("com.gargoylesoftware.htmlunit.ScriptResult");
            }
        }).forEach(importStmt -> {
            importStmt.delete();
        });
    }
}