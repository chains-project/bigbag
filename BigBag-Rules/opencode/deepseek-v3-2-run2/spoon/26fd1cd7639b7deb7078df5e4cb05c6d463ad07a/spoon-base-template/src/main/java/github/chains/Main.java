package github.chains;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.*;
import spoon.reflect.declaration.*;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.reference.CtVariableReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.*;

/**
 * Generic transformation to fix breaking change from HTMLUnit removal.
 * 
 * Breaking Change: Removal of com.gargoylesoftware.htmlunit.ScriptResult class
 * Old Pattern: PageObject.executeScript() returns Object, wrapped in ScriptResult
 * New Pattern: PageObject.executeScript() returns Object directly (no wrapper)
 * 
 * Transformation:
 * 1. Remove imports of com.gargoylesoftware.htmlunit.ScriptResult
 * 2. Replace new ScriptResult(expr) with expr
 * 3. Replace new ScriptResult(expr).getJavaScriptResult() with expr
 * 4. For variable assignments: ScriptResult var = new ScriptResult(expr) → Object var = expr
 * 5. Replace var.getJavaScriptResult() with var
 */
public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar spoon-transformation.jar <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        
        Launcher launcher = new Launcher();
        launcher.addInputResource(sourceDir);
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(true);
        
        launcher.addProcessor(new ScriptResultImportProcessor());
        launcher.addProcessor(new ScriptResultConstructorProcessor());
        launcher.addProcessor(new ScriptResultMethodCallProcessor());
        
        launcher.run();
    }
    
    /**
     * Processor to remove imports and type references of com.gargoylesoftware.htmlunit.ScriptResult
     */
    static class ScriptResultImportProcessor extends AbstractProcessor<CtTypeReference> {
        @Override
        public void process(CtTypeReference typeRef) {
            if (typeRef != null && "com.gargoylesoftware.htmlunit.ScriptResult".equals(typeRef.getQualifiedName())) {
                // Check if this is in an import statement
                CtImport ctImport = typeRef.getParent(CtImport.class);
                if (ctImport != null) {
                    ctImport.delete();
                }
            }
        }
    }
    
    /**
     * Processor to handle ScriptResult constructor calls and variable declarations
     */
    static class ScriptResultConstructorProcessor extends AbstractProcessor<CtConstructorCall<?>> {
        @Override
        public void process(CtConstructorCall<?> constructorCall) {
            CtTypeReference<?> constructedType = constructorCall.getType();
            if (constructedType != null && 
                "com.gargoylesoftware.htmlunit.ScriptResult".equals(constructedType.getQualifiedName())) {
                
                List<?> arguments = constructorCall.getArguments();
                if (arguments.size() == 1 && arguments.get(0) instanceof CtExpression) {
                    CtExpression<?> argExpr = (CtExpression<?>) arguments.get(0);
                    
                    // Check if this is in a method invocation chain
                    CtElement parent = constructorCall.getParent();
                    if (parent instanceof CtInvocation) {
                        CtInvocation<?> invocation = (CtInvocation<?>) parent;
                        // We'll handle this in ScriptResultMethodCallProcessor
                        return;
                    }
                    
                    // Check if assigned to a variable
                    if (parent instanceof CtVariable) {
                        CtVariable<?> variable = (CtVariable<?>) parent;
                        // Change variable type from ScriptResult to Object
                        variable.setType(getFactory().Type().OBJECT);
                    }
                    
                    // Replace constructor call with its argument
                    constructorCall.replace(argExpr);
                }
            }
        }
    }
    
    /**
     * Processor to handle getJavaScriptResult() method calls
     */
    static class ScriptResultMethodCallProcessor extends AbstractProcessor<CtInvocation<?>> {
        @Override
        public void process(CtInvocation<?> invocation) {
            CtExecutableReference<?> execRef = invocation.getExecutable();
            if (execRef != null && "getJavaScriptResult".equals(execRef.getSimpleName())) {
                
                CtExpression<?> target = invocation.getTarget();
                if (target != null) {
                    // Case 1: Direct chain: new ScriptResult(expr).getJavaScriptResult()
                    if (target instanceof CtConstructorCall) {
                        CtConstructorCall<?> constructorCall = (CtConstructorCall<?>) target;
                        CtTypeReference<?> constructedType = constructorCall.getType();
                        if (constructedType != null && 
                            "com.gargoylesoftware.htmlunit.ScriptResult".equals(constructedType.getQualifiedName())) {
                            
                            List<?> arguments = constructorCall.getArguments();
                            if (arguments.size() == 1 && arguments.get(0) instanceof CtExpression) {
                                CtExpression<?> argExpr = (CtExpression<?>) arguments.get(0);
                                // Replace entire invocation with the argument
                                invocation.replace(argExpr);
                            }
                        }
                    }
                    // Case 2: Variable.method(): var.getJavaScriptResult()
                    else if (target instanceof CtVariableRead) {
                        CtVariableRead<?> varRead = (CtVariableRead<?>) target;
                        // Just remove .getJavaScriptResult() - the variable already holds the Object
                        invocation.replace(varRead);
                    }
                }
            }
        }
    }
}