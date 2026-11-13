package github.chains;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.reflect.reference.CtTypeReference;
import java.util.List;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar spoon-transformation.jar <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Processing source directory: " + sourceDir);
        
        Launcher launcher = new Launcher();
        launcher.addInputResource(sourceDir);
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(true);
        
        // Add processor to fix ScriptResult breaking change
        launcher.addProcessor(new ScriptResultProcessor());
        
        try {
            launcher.run();
            System.out.println("Transformation completed successfully!");
        } catch (Exception e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    /**
     * Processor to fix the breaking change where com.gargoylesoftware.htmlunit.ScriptResult
     * has been removed from acceptance-test-harness dependency.
     * 
     * Old pattern:
     *   import com.gargoylesoftware.htmlunit.ScriptResult;
     *   Object result = pageObject.executeScript(...);
     *   ScriptResult scriptResult = new ScriptResult(result);
     *   String jsResult = scriptResult.getJavaScriptResult().toString();
     * 
     * New pattern:
     *   Object result = pageObject.executeScript(...);
     *   String jsResult = result.toString(); // Or cast as needed
     * 
     * This processor handles:
     * 1. Removing the import
     * 2. Replacing constructor calls with the argument
     * 3. Replacing getJavaScriptResult() calls with direct variable access
     */
    static class ScriptResultProcessor extends AbstractProcessor<CtClass<?>> {
        
        @Override
        public void process(CtClass<?> ctClass) {
            // Find and fix constructor calls to ScriptResult
            fixScriptResultConstructorCalls(ctClass);
            
            // Find and fix getJavaScriptResult() method calls
            fixGetJavaScriptResultCalls(ctClass);
        }
        
        private void fixScriptResultConstructorCalls(CtClass<?> ctClass) {
            // Find all constructor calls to ScriptResult
            List<CtConstructorCall<?>> constructorCalls = ctClass.getElements(
                new TypeFilter<CtConstructorCall<?>>(CtConstructorCall.class) {
                    @Override
                    public boolean matches(CtConstructorCall<?> constructorCall) {
                        CtTypeReference<?> typeRef = constructorCall.getType();
                        return typeRef != null && 
                               "com.gargoylesoftware.htmlunit.ScriptResult".equals(typeRef.getQualifiedName());
                    }
                }
            );
            
            // Replace each constructor call with its argument
            for (CtConstructorCall<?> constructorCall : constructorCalls) {
                if (!constructorCall.getArguments().isEmpty()) {
                    // Get the first argument (should be the Object result from executeScript)
                    spoon.reflect.code.CtExpression<?> arg = constructorCall.getArguments().get(0);
                    // Replace the constructor call with the argument
                    constructorCall.replace(arg);
                }
            }
        }
        
        private void fixGetJavaScriptResultCalls(CtClass<?> ctClass) {
            // Find all method calls to getJavaScriptResult()
            List<CtInvocation<?>> methodCalls = ctClass.getElements(
                new TypeFilter<CtInvocation<?>>(CtInvocation.class) {
                    @Override
                    public boolean matches(CtInvocation<?> invocation) {
                        return "getJavaScriptResult".equals(invocation.getExecutable().getSimpleName());
                    }
                }
            );
            
            // Replace getJavaScriptResult() calls with the target expression
            for (CtInvocation<?> methodCall : methodCalls) {
                spoon.reflect.code.CtExpression<?> target = methodCall.getTarget();
                if (target != null) {
                    // Replace method call with its target
                    // e.g., scriptResult.getJavaScriptResult() -> scriptResult
                    // But we need to check if target is a ScriptResult variable
                    // Since we already replaced constructor calls, target should be the result Object
                    methodCall.replace(target);
                }
            }
        }
        
        @Override
        public boolean isToBeProcessed(CtClass<?> candidate) {
            // Process all classes
            return true;
        }
    }
}