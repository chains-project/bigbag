package github.chains;

import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtTypeAccess;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtTypeReference;
import java.util.Collections;

/**
 * Generic transformation for tinspin-indexes 1.8.0 breaking changes.
 * Transforms deprecated API calls to their new equivalents.
 */
public class TinspinDeprecatedFixer extends AbstractProcessor<CtInvocation> {
    
    @Override
    public void process(CtInvocation invocation) {
        if (isDeprecatedKdTreeCreate(invocation)) {
            fixKdTreeCreate(invocation);
        }
        // Add other patterns here
    }
    
    private boolean isDeprecatedKdTreeCreate(CtInvocation invocation) {
        try {
            CtExecutableReference exec = invocation.getExecutable();
            if (exec == null || !"create".equals(exec.getSimpleName())) {
                return false;
            }
            
            // Check if target is KDTree type access
            if (invocation.getTarget() instanceof CtTypeAccess) {
                CtTypeAccess typeAccess = (CtTypeAccess) invocation.getTarget();
                CtTypeReference typeRef = typeAccess.getAccessedType();
                if (typeRef != null && "org.tinspin.index.kdtree.KDTree".equals(typeRef.getQualifiedName())) {
                    // Check number of parameters
                    return invocation.getArguments().size() == 2;
                }
            }
        } catch (Exception e) {
            // Ignore
        }
        return false;
    }
    
    private void fixKdTreeCreate(CtInvocation invocation) {
        try {
            Factory factory = getFactory();
            
            // Get the dimension argument
            var args = invocation.getArguments();
            var dimArg = args.get(0);
            
            // Create reference to KDTree class
            CtTypeReference kdTreeType = factory.createCtTypeReference(
                org.tinspin.index.kdtree.KDTree.class);
            
            // Create type access: KDTree
            CtTypeAccess typeAccess = factory.createTypeAccess(kdTreeType);
            
            // Create executable reference for create method with 1 parameter
            CtExecutableReference createMethod = factory.Core().createExecutableReference();
            createMethod.setSimpleName("create");
            createMethod.setDeclaringType(kdTreeType);
            createMethod.setStatic(true);
            
            // Set parameter type to int
            CtTypeReference intType = factory.createCtTypeReference(int.class);
            createMethod.setParameters(Collections.singletonList(intType));
            
            // Create new invocation
            CtInvocation newInvocation = factory.Core().createInvocation();
            newInvocation.setTarget(typeAccess);
            newInvocation.setExecutable(createMethod);
            newInvocation.setArguments(Collections.singletonList(factory.Core().clone((spoon.reflect.declaration.CtElement)dimArg)));
            
            // Replace old invocation
            invocation.replace(newInvocation);
            
            System.out.println("Transformed KDTree.create(dim, distFunc) -> KDTree.create(dim) at " + 
                invocation.getPosition());
                
        } catch (Exception e) {
            System.err.println("Failed to transform KDTree.create: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    @Override
    public boolean isToBeProcessed(CtInvocation element) {
        return isDeprecatedKdTreeCreate(element);
    }
}