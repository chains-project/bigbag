package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtVariableRead;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.declaration.CtVariable;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.Query;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.List;

/**
 * Generic Spoon transformation rule for fixing breaking API changes in acceptance-test-harness.
 * 
 * Breaking Change: PageObject.executeScript() no longer returns ScriptResult wrapper
 * Old pattern: ScriptResult scriptResult = new ScriptResult(pageObject.executeScript(...));
 * New pattern: Object result = pageObject.executeScript(...);
 * 
 * This transformation:
 * 1. Removes imports of com.gargoylesoftware.htmlunit.ScriptResult
 * 2. Replaces new ScriptResult(...) constructor calls with just the argument
 * 3. Replaces .getJavaScriptResult() method calls with just the target
 * 4. Changes variable declarations from ScriptResult to Object
 */
public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar spoon-transformation.jar <source-directory>");
            System.err.println("Applies transformation to fix ScriptResult API breaking change");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Transforming source directory: " + sourceDir);
        
        Launcher launcher = new Launcher();
        launcher.addInputResource(sourceDir);
        launcher.getEnvironment().setNoClasspath(false);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(true);
        
        // Build the model
        CtModel model = launcher.buildModel();
        
        // Get the factory from the model
        var factory = model.getRootPackage().getFactory();
        
        // Apply transformations in the correct order
        applyTransformations(factory);
        
        // Output the transformed code
        launcher.setSourceOutputDirectory(sourceDir);
        launcher.prettyprint();
        
        System.out.println("Transformation complete!");
    }
    
    private static void applyTransformations(spoon.reflect.factory.Factory factory) {
        // First, find and transform all variable declarations from ScriptResult to Object
        // This needs to happen before we try to build the model with type mismatches
        List<CtVariable> variables = Query.getElements(factory,
            new TypeFilter<CtVariable>(CtVariable.class) {
                @Override
                public boolean matches(CtVariable variable) {
                    CtTypeReference type = variable.getType();
                    return type != null && 
                           "ScriptResult".equals(type.getSimpleName());
                }
            });
        
        for (CtVariable var : variables) {
            System.out.println("Transforming ScriptResult variable: " + var.getSimpleName());
            // Change type from ScriptResult to Object
            var.setType(factory.Type().OBJECT);
        }
        
        // Remove imports of com.gargoylesoftware.htmlunit.ScriptResult
        List<CtImport> imports = Query.getElements(factory, new TypeFilter<CtImport>(CtImport.class) {
            @Override
            public boolean matches(CtImport ctImport) {
                String importStr = ctImport.toString();
                return importStr.contains("com.gargoylesoftware.htmlunit.ScriptResult") ||
                       importStr.contains("ScriptResult");
            }
        });
        
        for (CtImport imp : imports) {
            System.out.println("Removing import: " + imp);
            imp.delete();
        }
        
        // Transform ScriptResult constructor calls
        List<CtConstructorCall> constructorCalls = Query.getElements(factory, 
            new TypeFilter<CtConstructorCall>(CtConstructorCall.class) {
                @Override
                public boolean matches(CtConstructorCall call) {
                    return call.getType() != null &&
                           "ScriptResult".equals(call.getType().getSimpleName());
                }
            });
        
        for (CtConstructorCall call : constructorCalls) {
            System.out.println("Found ScriptResult constructor call: " + call);
            
            // Get the argument (should be the result of executeScript)
            if (call.getArguments().size() == 1) {
                // Replace the constructor call with its argument
                spoon.reflect.declaration.CtElement arg = (spoon.reflect.declaration.CtElement) call.getArguments().get(0);
                call.replace(arg);
                System.out.println("  Replaced with argument: " + arg);
            }
        }
        
        // Transform getJavaScriptResult() method calls
        List<CtInvocation> methodCalls = Query.getElements(factory,
            new TypeFilter<CtInvocation>(CtInvocation.class) {
                @Override
                public boolean matches(CtInvocation invocation) {
                    return invocation.getExecutable() != null &&
                           "getJavaScriptResult".equals(invocation.getExecutable().getSimpleName());
                }
            });
        
        for (CtInvocation call : methodCalls) {
            System.out.println("Found getJavaScriptResult() call: " + call);
            
            // Get the target of the method call (should be a ScriptResult instance)
            // We need to replace the entire method call with just the target
            if (call.getTarget() != null) {
                spoon.reflect.declaration.CtElement target = (spoon.reflect.declaration.CtElement) call.getTarget();
                call.replace(target);
                System.out.println("  Replaced with target: " + target);
            }
        }
    }
}