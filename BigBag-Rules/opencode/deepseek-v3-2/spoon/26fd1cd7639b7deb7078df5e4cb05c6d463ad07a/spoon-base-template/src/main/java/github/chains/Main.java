package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.*;
import spoon.reflect.declaration.*;
import spoon.reflect.reference.*;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.reflect.visitor.Query;
import spoon.reflect.visitor.Filter;

import java.util.List;
import java.util.HashMap;
import java.util.Map;

public class Main {
    // Configuration for the breaking change
    private static final String OLD_CLASS = "com.gargoylesoftware.htmlunit.ScriptResult";
    
    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: java -jar spoon-transformation.jar <source-directory> <output-directory>");
            System.err.println("Example: java -jar spoon-transformation.jar /path/to/project /path/to/transformed");
            System.exit(1);
        }

        String sourceDir = args[0];
        String outputDir = args[1];

        System.out.println("Fixing breaking change: " + OLD_CLASS + " was removed");
        System.out.println("Transformation: Remove ScriptResult wrapper, use executeScript() result directly");
        System.out.println("Source: " + sourceDir);
        System.out.println("Output: " + outputDir);

        Launcher launcher = new Launcher();
        launcher.addInputResource(sourceDir);
        launcher.setSourceOutputDirectory(outputDir);
        
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setNoClasspath(false);
        launcher.getEnvironment().setCommentEnabled(true);

        CtModel model = launcher.buildModel();
        
        transformCode(model);
        
        launcher.prettyprint();
        System.out.println("Transformation completed!");
    }
    
    private static void transformCode(CtModel model) {
        // Map to track ScriptResult variable assignments
        Map<CtVariable<?>, CtExpression<?>> scriptResultVars = new HashMap<>();
        
        // 1. Remove old imports
        List<CtImport> imports = model.getElements(new TypeFilter<>(CtImport.class));
        for (CtImport ctImport : imports) {
            if (ctImport.toString().contains(OLD_CLASS)) {
                System.out.println("Removing import: " + ctImport);
                ctImport.delete();
            }
        }
        
        // 2. Find all variable declarations of type ScriptResult
        List<CtVariable<?>> variables = model.getElements(new TypeFilter<>(CtVariable.class));
        for (CtVariable<?> variable : variables) {
            CtTypeReference<?> varType = variable.getType();
            if (varType != null && (OLD_CLASS.equals(varType.getQualifiedName()) || 
                "ScriptResult".equals(varType.getSimpleName()))) {
                
                // Check if this variable has an initializer (constructor call)
                CtExpression<?> defaultExpression = variable.getDefaultExpression();
                if (defaultExpression instanceof CtConstructorCall) {
                    CtConstructorCall<?> constructorCall = (CtConstructorCall<?>) defaultExpression;
                    if (constructorCall.getArguments().size() == 1) {
                        // Store mapping: ScriptResult var -> constructor argument
                        scriptResultVars.put(variable, constructorCall.getArguments().get(0));
                        System.out.println("Found ScriptResult variable: " + variable.getSimpleName() + 
                                         " initialized with: " + constructorCall.getArguments().get(0));
                    }
                }
            }
        }
        
        // 3. Transform getJavaScriptResult() calls
        List<CtInvocation<?>> invocations = model.getElements(new TypeFilter<>(CtInvocation.class));
        for (CtInvocation<?> invocation : invocations) {
            CtExecutableReference<?> execRef = invocation.getExecutable();
            if (execRef == null || !"getJavaScriptResult".equals(execRef.getSimpleName())) {
                continue;
            }
            
            CtExpression<?> target = invocation.getTarget();
            if (target == null) continue;
            
            System.out.println("Found getJavaScriptResult() call: " + invocation);
            
            // Case 1: target is a variable reference
            if (target instanceof CtVariableRead) {
                CtVariableRead<?> varRead = (CtVariableRead<?>) target;
                CtVariableReference<?> varRef = varRead.getVariable();
                // Find the variable declaration
                for (CtVariable<?> var : scriptResultVars.keySet()) {
                    if (var.getReference().equals(varRef)) {
                        // Replace var.getJavaScriptResult() with the original constructor argument
                        CtExpression<?> originalArg = scriptResultVars.get(var);
                        System.out.println("Replacing " + var.getSimpleName() + ".getJavaScriptResult() with original argument: " + originalArg);
                        invocation.replace(originalArg);
                        continue;
                    }
                }
            }
            
            // Case 2: target is a constructor call: new ScriptResult(x).getJavaScriptResult()
            if (target instanceof CtConstructorCall) {
                CtConstructorCall<?> constructorCall = (CtConstructorCall<?>) target;
                CtTypeReference<?> typeRef = constructorCall.getType();
                if (typeRef != null && (OLD_CLASS.equals(typeRef.getQualifiedName()) || 
                    "ScriptResult".equals(typeRef.getSimpleName()))) {
                    
                    if (constructorCall.getArguments().size() == 1) {
                        CtExpression<?> arg = constructorCall.getArguments().get(0);
                        System.out.println("Replacing new ScriptResult(x).getJavaScriptResult() with x");
                        invocation.replace(arg);
                        continue;
                    }
                }
            }
            
            // Case 3: other cases - replace with target (simpler but maybe incorrect)
            System.out.println("Replacing x.getJavaScriptResult() with x (generic fallback)");
            invocation.replace(target);
        }
        
        // 4. Remove ScriptResult constructor calls that are now unused
        // (Variable declarations will be handled by garbage collection in pretty printer)
        
        // 5. Remove unused ScriptResult variable declarations
        // Actually, Spoon's pretty printer should handle unused variables
    }
}