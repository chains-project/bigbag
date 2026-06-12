package github.chains;

import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtFieldRead;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtTypeReference;

public class FixGHCompareStatusAccessProcessor extends AbstractProcessor<CtFieldRead<?>> {
    @Override
    public void process(CtFieldRead<?> fieldRead) {
        if (!"status".equals(fieldRead.getVariable().getSimpleName())) {
            return;
        }
        
        System.out.println("Found 'status' field access at " + fieldRead.getPosition());
        CtTypeReference<?> declaringType = fieldRead.getVariable().getDeclaringType();
        System.out.println("Declaring type: " + (declaringType != null ? declaringType.getQualifiedName() : "null"));

        // Relaxed check:
        // If declaring type is null (common in noclasspath) or matches GHCompare
        boolean isGHCompare = declaringType != null && declaringType.getQualifiedName().contains("GHCompare");
        
        // Also check if we are in UpdateChecker.java
        boolean isUpdateChecker = fieldRead.getPosition().getFile().getName().equals("UpdateChecker.java");
        
        if (isGHCompare || isUpdateChecker) {
             // Create .getStatus() invocation
            // We need a reference to the method. 
            // In noclasspath, we might need to construct it carefully.
            
            CtTypeReference<?> targetType = declaringType;
            if (targetType == null) {
                // heuristic: create a reference to GHCompare
                targetType = getFactory().Type().createReference("org.kohsuke.github.GHCompare");
            }
            
            CtExecutableReference<?> getStatusRef = getFactory().Executable().createReference(
                    targetType,
                    fieldRead.getType(),
                    "getStatus"
            );
            
            CtInvocation<?> invocation = getFactory().Code().createInvocation(
                    fieldRead.getTarget(),
                    getStatusRef
            );
            
            fieldRead.replace(invocation);
            System.out.println("Replaced .status with .getStatus() at " + fieldRead.getPosition());
        }
    }
}