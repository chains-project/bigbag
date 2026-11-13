package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtStatement;
import spoon.reflect.code.CtAssignment;

import java.util.List;

/**
 * A minimal transformation to demonstrate how to fix ScriptResult usage
 * in acceptance test harness projects.
 */
public class Main {
    
    public static void main(String[] args) {
        // Parse the source code
        Launcher launcher = new Launcher();
        launcher.addInputResource("/workspace/code-coverage-api-plugin");
        
        // Build the model
        CtModel model = launcher.buildModel();
        
        // Find and process problematic elements
        fixScriptResultUsages(model);
        
        // Process and save
        launcher.process();
        launcher.setSourceOutputDirectory("/tmp/output");
        launcher.writeProcessedSourceFiles();
    }
    
    /**
     * Fix all occurrences of the ScriptResult usage pattern
     */
    private static void fixScriptResultUsages(CtModel model) {
        // Find all import statements that reference ScriptResult
        List<CtImport> scriptResultImports = model.getElements(new TypeFilter<>(CtImport.class) {
            @Override
            public boolean matches(CtImport element) {
                return element.getReference() != null && 
                       element.getReference().getSimpleName().equals("ScriptResult") &&
                       element.getReference().getPackage() != null &&
                       element.getReference().getPackage().toString().contains("htmlunit");
            }
        });
        
        // Remove the problematic imports
        for (CtImport imp : scriptResultImports) {
            imp.delete();
        }
        
        // Find all constructor calls to ScriptResult 
        List<CtConstructorCall> scriptResultCalls = model.getElements(new TypeFilter<>(CtConstructorCall.class) {
            @Override
            public boolean matches(CtConstructorCall element) {
                return element.getType() != null &&
                       element.getType().getSimpleName().equals("ScriptResult") &&
                       element.getType().getPackage() != null &&
                       element.getType().getPackage().toString().contains("htmlunit");
            }
        });
        
        // Replace ScriptResult constructor calls with the result directly
        for (CtConstructorCall call : scriptResultCalls) {
            if (!call.getArguments().isEmpty()) {
                // Replace with the first argument directly
                CtStatement parent = call.getParent(CtStatement.class);
                if (parent instanceof CtAssignment) {
                    CtAssignment<?> assignment = (CtAssignment<?>) parent;
                    assignment.setAssigned(call.getArguments().get(0));
                } else {
                    call.replace(call.getArguments().get(0));
                }
            }
        }
        
        // Find all method invocations that use ScriptResult.getJavaScriptResult()
        List<CtInvocation> scriptResultMethods = model.getElements(new TypeFilter<>(CtInvocation.class) {
            @Override
            public boolean matches(CtInvocation element) {
                return element.getExecutable() != null &&
                       element.getExecutable().getSimpleName().equals("getJavaScriptResult") &&
                       element.getTarget() != null &&
                       element.getTarget().getType() != null &&
                       element.getTarget().getType().getSimpleName().equals("ScriptResult");
            }
        });
        
        // Replace getJavaScriptResult() calls with direct access to the result
        for (CtInvocation invocation : scriptResultMethods) {
            // Replace with the target directly (the result)
            invocation.replace(invocation.getTarget());
        }
    }
}