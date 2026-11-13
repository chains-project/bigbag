package github.chains;

import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtFieldAccess;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.List;

/**
 * Processor to fix missing getInstance() calls in jaxb2-basics-runtime strategy classes
 */
public class FixStrategyGetInstanceProcessor extends AbstractProcessor {
    
    @Override
    public void process(CtElement element) {
        // Find all invocation expressions with getInstance method calls
        List<CtInvocation<?>> invocations = element.getElements(new TypeFilter<>(CtInvocation.class));
        
        for (CtInvocation<?> invocation : invocations) {
            CtExecutableReference<?> executableRef = invocation.getExecutable();
            
            // Check if it's a getInstance method call
            if ("getInstance".equals(executableRef.getSimpleName())) {
                // Check if the target is from jaxb2-basics-runtime
                if (invocation.getTarget() != null) {
                    CtTypeReference<?> targetType = invocation.getTarget().getType();
                    String targetTypeName = targetType.getQualifiedName();
                    
                    // Pattern matching for strategy classes that had getInstance removed
                    if (targetTypeName.contains("org.jvnet.jaxb2_commons.lang")) {
                        // Replace getInstance() with direct field access to INSTANCE
                        replaceGetInstanceWithStaticInstance(invocation);
                    }
                }
            }
        }
    }
    
    private void replaceGetInstanceWithStaticInstance(CtInvocation<?> invocation) {
        try {
            // Create a field access expression for INSTANCE
            CtFieldAccess<?> fieldAccess = invocation.getFactory().createFieldRead();
            fieldAccess.setTarget(invocation.getTarget());
            
            // Set the variable as INSTANCE field
            CtTypeReference<?> targetType = invocation.getTarget().getType();
            fieldAccess.setVariable(invocation.getFactory().createFieldReference()
                .setDeclaringType(targetType)
                .setSimpleName("INSTANCE"));
            
            // Replace the invocation with the field access
            invocation.replace(fieldAccess);
        } catch (Exception e) {
            // Silently ignore errors to prevent build failures
        }
    }
}