package github.chains;

import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.reference.CtExecutableReference;

public class ReflectionsFixProcessor extends AbstractProcessor<CtInvocation<?>> {
    @Override
    public void process(CtInvocation<?> invocation) {
        CtExecutableReference<?> executable = invocation.getExecutable();
        if (executable != null && "apply".equals(executable.getSimpleName())) {
            
            // Check chain: new FilterBuilder().include(...).apply(...)
            if (invocation.getTarget() instanceof CtInvocation) {
                CtInvocation<?> targetInvocation = (CtInvocation<?>) invocation.getTarget();
                if ("include".equals(targetInvocation.getExecutable().getSimpleName())) {
                     if (targetInvocation.getTarget() instanceof CtConstructorCall) {
                         CtConstructorCall<?> constructorCall = (CtConstructorCall<?>) targetInvocation.getTarget();
                         if ("FilterBuilder".equals(constructorCall.getType().getSimpleName())) {
                             executable.setSimpleName("test");
                             return;
                         }
                     }
                }
            }
            
            // Check chain: new FilterBuilder().apply(...) (if that ever happens)
            if (invocation.getTarget() instanceof CtConstructorCall) {
                 CtConstructorCall<?> constructorCall = (CtConstructorCall<?>) invocation.getTarget();
                 if ("FilterBuilder".equals(constructorCall.getType().getSimpleName())) {
                     executable.setSimpleName("test");
                 }
            }
        }
    }
}