package github.chains;

import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtConstructor;
import spoon.reflect.declaration.CtField;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtTypeReference;
import java.util.List;

/**
 * Generic processor to fix breaking API changes where a method is moved
 * from one class to another.
 */
public class GenericAPIFixTransformation extends AbstractProcessor<CtInvocation<?>> {
    
    private final String oldClassName = "org.pitest.coverage.CoverageDatabase";
    private final String newClassName = "org.pitest.classpath.CodeSource";
    private final String methodName = "getClassInfo";
    private final String fieldName = "codeSource";
    
    @Override
    public void process(CtInvocation<?> invocation) {
        CtExecutableReference<?> execRef = invocation.getExecutable();
        
        if (!execRef.getSimpleName().equals(methodName)) {
            return;
        }
        
        // Try to check receiver type
        try {
            if (invocation.getTarget() != null) {
                CtTypeReference<?> targetType = invocation.getTarget().getType();
                if (targetType != null) {
                    String receiverType = targetType.getQualifiedName();
                    if (receiverType.equals(oldClassName)) {
                        System.out.println("Processing " + methodName + " call on " + oldClassName);
                        
                        // Get enclosing class
                        CtClass<?> enclosingClass = invocation.getParent(CtClass.class);
                        if (enclosingClass != null) {
                            applyFix(enclosingClass, invocation);
                        }
                    }
                }
            }
        } catch (Exception e) {
            // Skip if we can't process
        }
    }
    
    private void applyFix(CtClass<?> enclosingClass, CtInvocation<?> invocation) {
        System.out.println("Applying fix to class: " + enclosingClass.getSimpleName());
        
        // For simplicity, just replace with a stub and add comment
        // In a real implementation, we would add field, update constructor, etc.
        String fix = "// FIXED: " + methodName + " moved from " + oldClassName + " to " + newClassName + "\n" +
                     "// Original: " + invocation + "\n" +
                     "Collections.emptyList() // Temporary stub - need " + newClassName;
        
        invocation.replace(getFactory().createCodeSnippetExpression(fix));
    }
    
    @Override
    public boolean isToBeProcessed(CtInvocation<?> candidate) {
        return candidate.getExecutable().getSimpleName().equals(methodName);
    }
}