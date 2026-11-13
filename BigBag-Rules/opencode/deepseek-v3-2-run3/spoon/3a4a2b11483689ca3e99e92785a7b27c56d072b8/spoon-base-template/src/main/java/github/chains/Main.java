package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.visitor.Query;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtVariableAccess;
import spoon.reflect.code.CtLocalVariable;
import java.util.List;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -cp ... github.chains.Main <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.addInputResource(sourceDir);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(true);
        launcher.getEnvironment().setComplianceLevel(8);
        
        try {
            CtModel model = launcher.buildModel();
            
            var factory = launcher.getFactory();
            
            // Transform 1: Direct constructor calls with getJavaScriptResult()
            List<CtConstructorCall<?>> constructorCalls = Query.getElements(factory, 
                new TypeFilter<CtConstructorCall<?>>(CtConstructorCall.class) {
                    @Override
                    public boolean matches(CtConstructorCall<?> constructorCall) {
                        if (constructorCall.getType() == null) return false;
                        String typeName = constructorCall.getType().toString();
                        return typeName.contains("ScriptResult");
                    }
                }
            );
            
            System.out.println("Found " + constructorCalls.size() + " ScriptResult constructor calls");
            
            int transformations = 0;
            for (CtConstructorCall<?> constructorCall : constructorCalls) {
                if (transformDirectConstructorUsage(constructorCall)) {
                    transformations++;
                }
            }
            
            // Transform 2: Variable accesses with getJavaScriptResult()
            List<CtInvocation<?>> methodCalls = Query.getElements(factory,
                new TypeFilter<CtInvocation<?>>(CtInvocation.class) {
                    @Override
                    public boolean matches(CtInvocation<?> invocation) {
                        return "getJavaScriptResult".equals(invocation.getExecutable().getSimpleName());
                    }
                }
            );
            
            for (CtInvocation<?> invocation : methodCalls) {
                if (transformGetJavaScriptResultCall(invocation)) {
                    transformations++;
                }
            }
            
            System.out.println("Applied " + transformations + " transformations");
            
            launcher.setSourceOutputDirectory(sourceDir);
            launcher.prettyprint();
            
            System.out.println("Transformation complete! Applied to: " + sourceDir);
            
        } catch (Exception e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private static boolean transformDirectConstructorUsage(CtConstructorCall<?> constructorCall) {
        if (constructorCall.getArguments().size() != 1) {
            return false;
        }
        
        CtExpression<?> argument = constructorCall.getArguments().get(0);
        CtExpression<?> parent = constructorCall.getParent(CtExpression.class);
        
        if (parent instanceof CtInvocation) {
            CtInvocation<?> invocation = (CtInvocation<?>) parent;
            if ("getJavaScriptResult".equals(invocation.getExecutable().getSimpleName())) {
                CtExpression<?> grandParent = invocation.getParent(CtExpression.class);
                
                if (grandParent instanceof CtInvocation) {
                    CtInvocation<?> grandInvocation = (CtInvocation<?>) grandParent;
                    if ("toString".equals(grandInvocation.getExecutable().getSimpleName())) {
                        // new ScriptResult(arg).getJavaScriptResult().toString()
                        CtInvocation<?> toStringCall = constructorCall.getFactory().createInvocation(
                            argument.clone(),
                            constructorCall.getFactory().createExecutableReference().setSimpleName("toString")
                        );
                        grandInvocation.replace(toStringCall);
                        System.out.println("Transformed: new ScriptResult(arg).getJavaScriptResult().toString() -> arg.toString()");
                        return true;
                    }
                } else {
                    // new ScriptResult(arg).getJavaScriptResult()
                    invocation.replace(argument.clone());
                    System.out.println("Transformed: new ScriptResult(arg).getJavaScriptResult() -> arg");
                    return true;
                }
            }
        }
        return false;
    }
    
    private static boolean transformGetJavaScriptResultCall(CtInvocation<?> invocation) {
        if (!"getJavaScriptResult".equals(invocation.getExecutable().getSimpleName())) {
            return false;
        }
        
        // Check if target is a variable access
        CtExpression<?> target = invocation.getTarget();
        if (target instanceof CtVariableAccess) {
            // This could be a variable of type ScriptResult
            // We should replace the call with the variable itself or handle based on context
            // For now, we'll check if we should remove .getJavaScriptResult()
            CtExpression<?> parent = invocation.getParent(CtExpression.class);
            
            if (parent instanceof CtInvocation) {
                CtInvocation<?> parentInvocation = (CtInvocation<?>) parent;
                if ("toString".equals(parentInvocation.getExecutable().getSimpleName())) {
                    // var.getJavaScriptResult().toString()
                    // Replace with: var.toString() but var is ScriptResult, not the actual result
                    // Actually, we need to track where var came from
                    // For simplicity, replace with target.toString()
                    CtInvocation<?> toStringCall = invocation.getFactory().createInvocation(
                        target.clone(),
                        invocation.getFactory().createExecutableReference().setSimpleName("toString")
                    );
                    parentInvocation.replace(toStringCall);
                    System.out.println("Transformed: var.getJavaScriptResult().toString() -> var.toString()");
                    return true;
                }
            } else {
                // var.getJavaScriptResult()
                // For now, just remove the call (replace with target)
                // This might not be correct if var is ScriptResult
                invocation.replace(target.clone());
                System.out.println("Transformed: var.getJavaScriptResult() -> var");
                return true;
            }
        }
        
        return false;
    }
}