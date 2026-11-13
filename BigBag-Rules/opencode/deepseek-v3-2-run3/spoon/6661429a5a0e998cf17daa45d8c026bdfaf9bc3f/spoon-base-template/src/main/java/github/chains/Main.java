package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.*;
import spoon.reflect.declaration.*;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.reflect.reference.CtTypeReference;
import java.util.List;
import java.util.ArrayList;

/**
 * Generic Spoon transformation to fix breaking API change:
 * org.jenkins-ci:acceptance-test-harness removed dependency on htmlunit's ScriptResult.
 * 
 * Old API pattern: ScriptResult wrapper = new ScriptResult(executeScriptResult);
 * New API pattern: executeScript returns Object directly, no ScriptResult wrapper needed.
 * 
 * Transformation handles:
 * 1. new ScriptResult(result).getJavaScriptResult() → result
 * 2. new ScriptResult(result).getJavaScriptResult().toString() → result.toString()
 * 3. ScriptResult var = new ScriptResult(result); return var.getJavaScriptResult().toString() → return result.toString()
 * 4. Removes imports of com.gargoylesoftware.htmlunit.ScriptResult
 */
public class Main {
    private static final String SCRIPT_RESULT_FQN = "com.gargoylesoftware.htmlunit.ScriptResult";
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar spoon-transformation.jar <source-directory>");
            System.err.println("Applies transformation to fix ScriptResult API breaking change");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Processing source directory: " + sourceDir);
        
        Launcher launcher = new Launcher();
        launcher.addInputResource(sourceDir);
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(true);
        
        try {
            CtModel model = launcher.buildModel();
            
            // Find all constructor calls to com.gargoylesoftware.htmlunit.ScriptResult
            List<CtConstructorCall<?>> scriptResultCtorCalls = model
                .getElements(new TypeFilter<CtConstructorCall<?>>(CtConstructorCall.class) {
                    @Override
                    public boolean matches(CtConstructorCall<?> constructorCall) {
                        CtTypeReference<?> typeRef = constructorCall.getType();
                        return typeRef != null && SCRIPT_RESULT_FQN.equals(typeRef.getQualifiedName());
                    }
                });
            
            System.out.println("Found " + scriptResultCtorCalls.size() + " ScriptResult constructor calls");
            
            // Process each constructor call
            for (CtConstructorCall<?> ctorCall : new ArrayList<>(scriptResultCtorCalls)) {
                processScriptResultConstructor(launcher, ctorCall);
            }
            
            // Remove imports of com.gargoylesoftware.htmlunit.ScriptResult
            List<CtImport> importsToRemove = new ArrayList<>();
            model.getElements(new TypeFilter<CtImport>(CtImport.class) {
                @Override
                public boolean matches(CtImport importDecl) {
                    return importDecl.toString().contains(SCRIPT_RESULT_FQN);
                }
            }).forEach(importsToRemove::add);
            
            for (CtImport importDecl : importsToRemove) {
                System.out.println("Removing import: " + importDecl);
                importDecl.delete();
            }
            
            // Output the transformed code
            launcher.setSourceOutputDirectory(sourceDir);
            launcher.prettyprint();
            
            System.out.println("Transformation complete! Processed " + scriptResultCtorCalls.size() + 
                             " constructor calls and removed " + importsToRemove.size() + " imports.");
            
        } catch (Exception e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static void processScriptResultConstructor(Launcher launcher, CtConstructorCall<?> ctorCall) {
        System.out.println("Processing ScriptResult constructor at: " + ctorCall.getPosition());
        
        List<CtExpression<?>> argsList = ctorCall.getArguments();
        if (argsList.size() != 1) {
            System.out.println("  Warning: ScriptResult constructor with " + argsList.size() + " arguments, expected 1");
            return;
        }
        
        CtExpression<?> arg = argsList.get(0);
        
        // Check if this is part of a method call chain: new ScriptResult(result).getJavaScriptResult()
        CtInvocation parentInvocation = ctorCall.getParent(CtInvocation.class);
        if (parentInvocation != null && "getJavaScriptResult".equals(parentInvocation.getExecutable().getSimpleName())) {
            handleGetJavaScriptResultChain(launcher, parentInvocation, arg);
            return;
        }
        
        // Check if it's a variable assignment: ScriptResult var = new ScriptResult(result);
        CtAssignment assignment = ctorCall.getParent(CtAssignment.class);
        if (assignment != null && assignment.getAssigned() == ctorCall) {
            handleVariableAssignment(launcher, assignment, arg);
            return;
        }
        
        // Direct constructor call as expression
        ctorCall.replace(arg.clone());
        System.out.println("  Fixed direct constructor call");
    }
    
    private static void handleGetJavaScriptResultChain(Launcher launcher, CtInvocation getJavaScriptResultInvocation, 
                                                     CtExpression<?> arg) {
        // Check if there are further method calls after getJavaScriptResult()
        CtInvocation nextInvocation = getJavaScriptResultInvocation.getParent(CtInvocation.class);
        if (nextInvocation != null) {
            // Pattern: new ScriptResult(result).getJavaScriptResult().something()
            handleChainedMethodCall(launcher, nextInvocation, arg);
        } else {
            // Pattern: new ScriptResult(result).getJavaScriptResult() 
            getJavaScriptResultInvocation.replace(arg.clone());
            System.out.println("  Fixed pattern: new ScriptResult(...).getJavaScriptResult()");
        }
    }
    
    private static void handleChainedMethodCall(Launcher launcher, CtInvocation methodInvocation, CtExpression<?> arg) {
        String methodName = methodInvocation.getExecutable().getSimpleName();
        
        if ("toString".equals(methodName)) {
            methodInvocation.replace(launcher.getFactory().createCodeSnippetExpression(arg.toString() + ".toString()"));
            System.out.println("  Fixed pattern: new ScriptResult(...).getJavaScriptResult().toString()");
        } else {
            // Generic case: replace with arg.methodName(...)
            StringBuilder replacement = new StringBuilder(arg.toString());
            replacement.append(".").append(methodName).append("(");
            List<CtExpression<?>> methodArgs = methodInvocation.getArguments();
            for (int i = 0; i < methodArgs.size(); i++) {
                if (i > 0) replacement.append(", ");
                replacement.append(methodArgs.get(i).toString());
            }
            replacement.append(")");
            methodInvocation.replace(launcher.getFactory().createCodeSnippetExpression(replacement.toString()));
            System.out.println("  Fixed pattern: new ScriptResult(...).getJavaScriptResult()." + methodName + "(...)");
        }
    }
    
    private static void handleVariableAssignment(Launcher launcher, CtAssignment assignment, CtExpression<?> arg) {
        // Pattern: ScriptResult var = new ScriptResult(result);
        CtExpression<?> variableExpr = assignment.getAssigned();
        if (variableExpr instanceof CtVariableWrite) {
            CtVariableWrite<?> varWrite = (CtVariableWrite<?>) variableExpr;
            String varName = varWrite.getVariable().getSimpleName();
            
            // Look for usages of this variable in the same method
            CtMethod<?> method = assignment.getParent(CtMethod.class);
            if (method != null) {
                handleVariableUsages(launcher, method, varName, assignment);
            }
            
            // Replace RHS with the argument
            assignment.setAssignment(arg.clone());
            System.out.println("  Fixed pattern: ScriptResult var = new ScriptResult(...)");
        } else {
            assignment.setAssignment(arg.clone());
            System.out.println("  Fixed assignment");
        }
    }
    
    private static void handleVariableUsages(Launcher launcher, CtMethod<?> method, String varName, 
                                           CtAssignment originalAssignment) {
        // Find all getJavaScriptResult() calls on this variable
        List<CtInvocation<?>> invocations = method.getElements(new TypeFilter<CtInvocation<?>>(CtInvocation.class) {
            @Override
            public boolean matches(CtInvocation<?> invocation) {
                if (!"getJavaScriptResult".equals(invocation.getExecutable().getSimpleName())) {
                    return false;
                }
                CtExpression<?> target = invocation.getTarget();
                return target instanceof CtVariableRead && 
                       varName.equals(((CtVariableRead<?>) target).getVariable().getSimpleName());
            }
        });
        
        for (CtInvocation<?> invocation : invocations) {
            // Replace var.getJavaScriptResult() with var
            invocation.replace(launcher.getFactory().createCodeSnippetExpression(varName));
            
            // Check for chained method calls
            CtInvocation nextInvocation = invocation.getParent(CtInvocation.class);
            if (nextInvocation != null && nextInvocation.getTarget() == invocation) {
                String nextMethodName = nextInvocation.getExecutable().getSimpleName();
                List<CtExpression<?>> nextArgs = nextInvocation.getArguments();
                StringBuilder replacement = new StringBuilder(varName).append(".").append(nextMethodName).append("(");
                for (int i = 0; i < nextArgs.size(); i++) {
                    if (i > 0) replacement.append(", ");
                    replacement.append(nextArgs.get(i).toString());
                }
                replacement.append(")");
                nextInvocation.replace(launcher.getFactory().createCodeSnippetExpression(replacement.toString()));
            }
        }
        
        // If the variable is only used with getJavaScriptResult(), we could optimize further
        // by inlining the expression and removing the variable declaration entirely.
        // For simplicity, we leave that as a manual optimization.
    }
}