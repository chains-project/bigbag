package github.chains;

import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtAssignment;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtMethodCall;
import spoon.reflect.code.CtNewClass;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtVariable;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtTypeReference;

/**
 * Generic transformation rule to replace ScriptResult usage with direct Object handling.
 * This rule handles the breaking change where ScriptResult class was removed from the dependency.
 * 
 * Before: ScriptResult scriptResult = new ScriptResult(result); return scriptResult.getJavaScriptResult().toString();
 * After: return result.toString();
 */
public class Main extends AbstractProcessor&lt;CtMethod&lt;?&gt;&gt; {

    @Override
    public void process(CtMethod&lt;?&gt; method) {
        // Process all assignments in the method
        for (CtAssignment&lt;?, ?&gt; assignment : method.getElements(e -> e instanceof CtAssignment)) {
            // Check if this assignment creates a ScriptResult instance
            if (assignment.getRightHandOperand() instanceof CtNewClass) {
                CtNewClass&lt;?&gt; newClass = (CtNewClass&lt;?&gt;) assignment.getRightHandOperand();
                CtTypeReference&lt;?&gt; typeRef = newClass.getExecutable().getDeclaringType();
                
                // Check if it's creating a ScriptResult
                if (typeRef != null && typeRef.getQualifiedName().equals("com.gargoylesoftware.htmlunit.ScriptResult")) {
                    // This is a ScriptResult creation: ScriptResult var = new ScriptResult(result);
                    CtExpression&lt;?&gt; lhs = assignment.getLeftHandOperand();
                    CtExpression&lt;?&gt; rhs = assignment.getRightHandOperand();
                    
                    // Get the argument passed to ScriptResult constructor
                    if (rhs instanceof CtNewClass&lt;?&gt; && !((CtNewClass&lt;?&gt;) rhs).getArguments().isEmpty()) {
                        CtExpression&lt;?&gt; constructorArg = ((CtNewClass&lt;?&gt;) rhs).getArguments().get(0);
                        
                        // Now find all usages of this ScriptResult variable that call getJavaScriptResult().toString()
                        String varName = lhs.toString();
                        
                        // Find method calls that use the ScriptResult variable
                        for (CtMethodCall&lt;?&gt; methodCall : method.getElements(e -> e instanceof CtMethodCall)) {
                            if (methodCall.getExecutable().getSimpleName().equals("getJavaScriptResult") 
                                && methodCall.getTarget() != null 
                                && methodCall.getTarget().toString().equals(varName)) {
                                
                                // This is a call like: scriptResult.getJavaScriptResult()
                                // Find the parent call to .toString()
                                CtExpression&lt;?&gt; parent = methodCall.getParent();
                                if (parent instanceof CtMethodCall&lt;?&gt; 
                                    && ((CtMethodCall&lt;?&gt;) parent).getExecutable().getSimpleName().equals("toString")) {
                                    
                                    // Replace the entire chain with: constructorArg.toString()
                                    CtMethodCall&lt;?&gt; newCall = method.getFactory().createMethodCall();
                                    newCall.setTarget(constructorArg);
                                    newCall.setExecutable(method.getFactory().createMethodReference());
                                    newCall.getExecutable().setSimpleName("toString");
                                    
                                    parent.replace(newCall);
                                }
                            }
                        }
                        
                        // Replace the assignment with just the result variable
                        assignment.replace(constructorArg);
                    }
                }
            }
        }
    }

    @Override
    public boolean isToBeProcessed(CtMethod&lt;?&gt; element) {
        // Process all methods
        return true;
    }
}
                                }
                                
                                if (constructorArg != null) {
                                    // Now look for calls to getJavaScriptResult() on this variable
                                    // We need to find: scriptResult.getJavaScriptResult().toString()
                                    // and replace with: result.toString()
                                    
                                    // Get the variable name from left-hand side
                                    String varName = leftHandSide.toString();
                                    
                                    // Find all method calls to getJavaScriptResult() on this variable
                                    for (CtMethodCall&lt;?&gt; methodCall : type.getElements(
                                            new TypeReferenceFilter&lt;CtTypeReference&lt;?&gt;&gt;(SCRIPT_RESULT_TYPE))
                                            .stream()
                                            .map(ref2 -> ref2.getParent())
                                            .filter(p -> p instanceof CtMethodCall)
                                            .map(p -> (CtMethodCall&lt;?&gt;) p)
                                            .filter(call -> call.getExecutable().getSimpleName().equals(GET_JAVASCRIPT_RESULT_METHOD))
                                            .filter(call -> call.getTarget().toString().equals(varName))
                                            .toList()) {
                                        
                                        // Get the parent of the getJavaScriptResult() call
                                        CtExpression&lt;?&gt; parent = methodCall.getParent();
                                        
                                        // If this is a method call on getJavaScriptResult(), we want to replace
                                        // scriptResult.getJavaScriptResult().toString() with result.toString()
                                        
                                        // Find the .toString() call on getJavaScriptResult()
                                        if (parent instanceof CtMethodCall&lt;?&gt;) {
                                            CtMethodCall&lt;?&gt; toStringCall = (CtMethodCall&lt;?&gt;) parent;
                                            if (toStringCall.getExecutable().getSimpleName().equals("toString")) {
                                                
                                                // Replace the entire chain: scriptResult.getJavaScriptResult().toString()
                                                // with: result.toString()
                                                
                                                // Create a new method call: constructorArg.toString()
                                                Factory factory = method.getFactory();
                                                CtMethodCall&lt;?&gt; newCall = factory.createMethodCall();
                                                newCall.setTarget(constructorArg);
                                                newCall.setExecutable(factory.createMethodReference());
                                                newCall.getExecutable().setSimpleName("toString");
                                                
                                                // Replace the old expression with the new one
                                                toStringCall.replace(newCall);
                                            }
                                        }
                                    }
                                    
                                    // Remove the ScriptResult assignment completely
                                    assignment.replace(constructorArg);
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Override
    public boolean isToBeProcessed(CtMethod&lt;?&gt; element) {
        // Process all methods
        return true;
    }
}