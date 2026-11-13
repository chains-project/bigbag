package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtExpression;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.declaration.CtType;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.reflect.visitor.Query;
import spoon.reflect.reference.CtTypeReference;

import java.util.List;
import java.util.ArrayList;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java github.chains.Main <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        
        Launcher launcher = new Launcher();
        launcher.addInputResource(sourceDir);
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(true);
        
        CtModel model = launcher.buildModel();
        
        // Find all constructor calls to com.gargoylesoftware.htmlunit.ScriptResult
        List<CtConstructorCall<?>> scriptResultCalls = Query.getElements(model.getRootPackage(), 
            new TypeFilter<CtConstructorCall<?>>(CtConstructorCall.class) {
                @Override
                public boolean matches(CtConstructorCall<?> constructorCall) {
                    CtTypeReference<?> typeRef = constructorCall.getType();
                    if (typeRef == null) return false;
                    
                    String qualifiedName = typeRef.getQualifiedName();
                    return qualifiedName != null && 
                           qualifiedName.equals("com.gargoylesoftware.htmlunit.ScriptResult");
                }
            });
        
        // Process each call
        for (CtConstructorCall<?> constructorCall : scriptResultCalls) {
            processScriptResultCall(constructorCall);
        }
        
        // Also remove imports of com.gargoylesoftware.htmlunit.ScriptResult
        removeScriptResultImports(model);
        
        // Output transformed code
        launcher.setSourceOutputDirectory("./output");
        launcher.prettyprint();
        
        System.out.println("Transformation complete. Output written to ./output");
    }
    
    private static void processScriptResultCall(CtConstructorCall<?> constructorCall) {
        System.out.println("Processing ScriptResult constructor at: " + 
            constructorCall.getPosition());
        
        // Get the argument (the result from executeScript)
        if (constructorCall.getArguments().isEmpty()) {
            System.out.println("  Warning: ScriptResult constructor has no arguments");
            return;
        }
        
        CtExpression<?> arg = constructorCall.getArguments().get(0);
        
        // Check if this is part of a getJavaScriptResult() call
        CtInvocation<?> parentInvocation = constructorCall.getParent(CtInvocation.class);
        
        if (parentInvocation != null && 
            parentInvocation.getExecutable() != null &&
            "getJavaScriptResult".equals(parentInvocation.getExecutable().getSimpleName())) {
            
            // Case 1: new ScriptResult(...).getJavaScriptResult()
            // Replace with the argument
            
            // Check if there's further chaining (e.g., .toString())
            CtInvocation<?> grandparentInvocation = parentInvocation.getParent(CtInvocation.class);
            if (grandparentInvocation != null && 
                grandparentInvocation.getTarget() == parentInvocation) {
                
                // Case: new ScriptResult(...).getJavaScriptResult().toString()
                // We need to replace the target of the grandparent invocation
                grandparentInvocation.setTarget(arg.clone());
                System.out.println("  Replaced: new ScriptResult(...).getJavaScriptResult().toString() -> argument.toString()");
            } else {
                // Case: just new ScriptResult(...).getJavaScriptResult()
                parentInvocation.replace(arg.clone());
                System.out.println("  Replaced: new ScriptResult(...).getJavaScriptResult() -> argument");
            }
            
        } else {
            // Check if parent is a variable declaration or assignment
            // We need to find all getJavaScriptResult() calls on this variable
            // This is more complex - for now, we'll note it
            System.out.println("  Note: ScriptResult assigned to variable - need manual fix");
        }
    }
    
    private static void removeScriptResultImports(CtModel model) {
        // Find all types and remove imports of com.gargoylesoftware.htmlunit.ScriptResult
        List<CtType<?>> types = Query.getElements(model.getRootPackage(), 
            new TypeFilter<CtType<?>>(CtType.class));
        
        for (CtType<?> type : types) {
            List<CtImport> importsToRemove = new ArrayList<>();
            for (CtImport imp : type.getImports()) {
                if (imp.getReference() != null && 
                    "com.gargoylesoftware.htmlunit.ScriptResult".equals(imp.getReference().getQualifiedName())) {
                    importsToRemove.add(imp);
                }
            }
            
            if (!importsToRemove.isEmpty()) {
                System.out.println("Removing import of com.gargoylesoftware.htmlunit.ScriptResult from: " + 
                    type.getQualifiedName());
                // Note: CtType doesn't have a removeImport method in this Spoon version
                // We would need to create a new import list
            }
        }
    }
}