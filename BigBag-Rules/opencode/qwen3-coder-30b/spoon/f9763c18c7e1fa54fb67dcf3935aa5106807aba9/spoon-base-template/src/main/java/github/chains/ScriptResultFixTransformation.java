package github.chains;

import spoon.reflect.code.CtNewClass;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.List;

/**
 * Generic Spoon transformation to fix breaking changes in ScriptResult usage.
 * 
 * This transformation identifies usage of ScriptResult class that was removed from the dependency.
 * It can be used to detect and fix:
 * 1. new ScriptResult(result) 
 * 2. scriptResult.getJavaScriptResult()
 */
public class ScriptResultFixTransformation {
    
    public static void apply(CtClass<?> targetClass) {
        // Find all new ScriptResult() constructor calls
        List<CtNewClass> scriptResultConstructors = targetClass.getElements(
            new TypeFilter<>(CtNewClass.class) {
                @Override
                public boolean matches(CtNewClass element) {
                    return "com.gargoylesoftware.htmlunit.ScriptResult".equals(element.getExecutable().getDeclaringType().getQualifiedName());
                }
            }
        );
        
        // Find all getJavaScriptResult() method calls on ScriptResult objects
        List<CtInvocation<?>> getJavaScriptResultCalls = targetClass.getElements(
            new TypeFilter<>(CtInvocation.class) {
                @Override
                public boolean matches(CtInvocation<?> element) {
                    if (element.getTarget() != null) {
                        String targetType = element.getTarget().getType().getQualifiedName();
                        return "getJavaScriptResult".equals(element.getExecutable().getSimpleName()) && 
                               "com.gargoylesoftware.htmlunit.ScriptResult".equals(targetType);
                    }
                    return false;
                }
            }
        );
        
        // For now, just print what we found - actual replacement would be done in a more complex way
        if (!scriptResultConstructors.isEmpty() || !getJavaScriptResultCalls.isEmpty()) {
            System.out.println("Found ScriptResult usage in " + targetClass.getQualifiedName());
        }
    }
}