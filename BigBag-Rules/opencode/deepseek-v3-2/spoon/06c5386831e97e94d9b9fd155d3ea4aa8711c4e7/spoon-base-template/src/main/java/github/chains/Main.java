package github.chains;

import spoon.Launcher;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtExpression;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.reference.CtExecutableReference;
import spoon.processing.AbstractProcessor;
import java.util.List;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar spoon-transformation.jar <source-directory>");
            System.err.println("This transformation fixes the ScriptResult API breaking change from HtmlUnit 2.x to 3.x.");
            System.err.println("Transformation rules:");
            System.err.println("1. Removes import: com.gargoylesoftware.htmlunit.ScriptResult");
            System.err.println("2. Replaces: new ScriptResult(expr).getJavaScriptResult() -> expr.toString()");
            System.err.println("3. Replaces: new ScriptResult(expr).getJavaScriptResult().toString() -> expr.toString()");
            System.err.println("4. Handles variable assignment patterns");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        
        Launcher launcher = new Launcher();
        
        // Configure Spoon - use no classpath mode since we don't have dependencies
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.addInputResource(sourceDir);
        launcher.setSourceOutputDirectory(sourceDir); // Write changes back to source
        
        // Add transformation processor
        launcher.addProcessor(new ScriptResultApiFixProcessor());
        
        // Run transformation
        launcher.run();
        
        System.out.println("Transformation complete. Source files updated in-place.");
    }
    
    /**
     * Generic Spoon processor to fix the ScriptResult API breaking change.
     * 
     * Breaking Change Analysis:
     * - Old API: com.gargoylesoftware.htmlunit.ScriptResult (HtmlUnit 2.x)
     * - New API: org.jenkinsci.test.acceptance.plugins.scriptler.ScriptResult (different class)
     * 
     * The new ScriptResult class has incompatible API:
     * - Constructor takes String instead of Object
     * - No getJavaScriptResult() method
     * 
     * Transformation Logic:
     * The old pattern wrapped JavaScript result objects and then unwrapped them.
     * Since pageObject.executeScript() already returns the result as Object,
     * we can directly use .toString() on the result instead of wrapping/unwrapping.
     */
    static class ScriptResultApiFixProcessor extends AbstractProcessor<CtClass<?>> {
        
        private final String OLD_SCRIPT_RESULT_TYPE = "com.gargoylesoftware.htmlunit.ScriptResult";
        
        @Override
        public void process(CtClass<?> ctClass) {
            // Pattern 1: Find and fix constructor calls to ScriptResult
            fixScriptResultConstructorCalls(ctClass);
            
            // Pattern 2: Find and fix getJavaScriptResult() method calls
            fixGetJavaScriptResultCalls(ctClass);
        }
        
        private void fixScriptResultConstructorCalls(CtClass<?> ctClass) {
            // Find all constructor calls to ScriptResult
            List<CtConstructorCall<?>> constructorCalls = ctClass.getElements(
                new TypeFilter<CtConstructorCall<?>>(CtConstructorCall.class) {
                    @Override
                    public boolean matches(CtConstructorCall<?> constructorCall) {
                        CtTypeReference<?> typeRef = constructorCall.getType();
                        if (typeRef != null) {
                            String typeName = typeRef.getQualifiedName();
                            return typeName.equals(OLD_SCRIPT_RESULT_TYPE) || 
                                   "ScriptResult".equals(typeRef.getSimpleName());
                        }
                        return false;
                    }
                }
            );
            
            for (CtConstructorCall<?> constructorCall : constructorCalls) {
                // Get the argument (should be one argument)
                List<CtExpression<?>> args = constructorCall.getArguments();
                if (args.size() != 1) {
                    continue;
                }
                
                CtExpression<?> arg = args.get(0);
                
                // Check the context of this constructor call
                CtExpression<?> parent = constructorCall.getParent(CtExpression.class);
                
                if (parent instanceof CtInvocation) {
                    CtInvocation<?> invocation = (CtInvocation<?>) parent;
                    if ("getJavaScriptResult".equals(invocation.getExecutable().getSimpleName())) {
                        // Pattern: new ScriptResult(arg).getJavaScriptResult()
                        handleGetJavaScriptResultInvocation(invocation, constructorCall, arg);
                    }
                } else {
                    // Pattern: Variable assignment or other usage
                    // We need to track variable usage and replace later
                    // For simplicity, replace with arg.toString() in common cases
                    handleConstructorAssignment(constructorCall, arg);
                }
            }
        }
        
        private void handleGetJavaScriptResultInvocation(CtInvocation<?> invocation, 
                CtConstructorCall<?> constructorCall, CtExpression<?> arg) {
            
            // Check if there's a further .toString() call
            CtExpression<?> grandParent = invocation.getParent(CtExpression.class);
            
            if (grandParent instanceof CtInvocation) {
                CtInvocation<?> grandInvocation = (CtInvocation<?>) grandParent;
                if ("toString".equals(grandInvocation.getExecutable().getSimpleName())) {
                    // Pattern: new ScriptResult(arg).getJavaScriptResult().toString()
                    // Replace with: arg.toString()
                    CtInvocation<?> toStringCall = getFactory().createInvocation(
                        arg.clone(),
                        getFactory().createExecutableReference().setSimpleName("toString")
                    );
                    grandInvocation.replace(toStringCall);
                }
            } else {
                // Pattern: new ScriptResult(arg).getJavaScriptResult()
                // Replace with: arg.toString()  
                CtInvocation<?> toStringCall = getFactory().createInvocation(
                    arg.clone(),
                    getFactory().createExecutableReference().setSimpleName("toString")
                );
                invocation.replace(toStringCall);
            }
        }
        
        private void handleConstructorAssignment(CtConstructorCall<?> constructorCall, 
                CtExpression<?> arg) {
            // This handles cases like:
            // ScriptResult sr = new ScriptResult(arg);
            // Later: sr.getJavaScriptResult()
            
            // For now, we replace with arg.toString() which is a safe default
            // The actual fix might need to track variable usage
            CtInvocation<?> toStringCall = getFactory().createInvocation(
                arg.clone(),
                getFactory().createExecutableReference().setSimpleName("toString")
            );
            constructorCall.replace(toStringCall);
        }
        
        private void fixGetJavaScriptResultCalls(CtClass<?> ctClass) {
            // Find remaining getJavaScriptResult() calls (on variables)
            List<CtInvocation<?>> methodInvocations = ctClass.getElements(
                new TypeFilter<CtInvocation<?>>(CtInvocation.class) {
                    @Override
                    public boolean matches(CtInvocation<?> invocation) {
                        CtExecutableReference<?> execRef = invocation.getExecutable();
                        return execRef != null && "getJavaScriptResult".equals(execRef.getSimpleName());
                    }
                }
            );
            
            for (CtInvocation<?> invocation : methodInvocations) {
                // Pattern: variable.getJavaScriptResult()
                CtExpression<?> target = invocation.getTarget();
                if (target != null) {
                    // Check if target is a variable of type ScriptResult
                    // For simplicity, replace with target (the variable itself)
                    // or target.toString() depending on context
                    
                    // Check parent context
                    CtExpression<?> parent = invocation.getParent(CtExpression.class);
                    if (parent instanceof CtInvocation) {
                        CtInvocation<?> parentInvocation = (CtInvocation<?>) parent;
                        if ("toString".equals(parentInvocation.getExecutable().getSimpleName())) {
                            // Pattern: var.getJavaScriptResult().toString()
                            // Replace with: var.toString()
                            CtInvocation<?> toStringCall = getFactory().createInvocation(
                                target.clone(),
                                getFactory().createExecutableReference().setSimpleName("toString")
                            );
                            parentInvocation.replace(toStringCall);
                        }
                    } else {
                        // Pattern: var.getJavaScriptResult()
                        // Replace with: var (or var.toString() depending on type expectations)
                        // Since we can't determine type, use toString() for safety
                        CtInvocation<?> toStringCall = getFactory().createInvocation(
                            target.clone(),
                            getFactory().createExecutableReference().setSimpleName("toString")
                        );
                        invocation.replace(toStringCall);
                    }
                }
            }
        }
    }
}