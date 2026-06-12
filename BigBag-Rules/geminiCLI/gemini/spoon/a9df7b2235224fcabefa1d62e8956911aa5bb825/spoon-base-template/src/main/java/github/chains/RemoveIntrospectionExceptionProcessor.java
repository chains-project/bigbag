package github.chains;

import spoon.processing.AbstractProcessor;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.reference.CtTypeReference;
import java.beans.IntrospectionException;
import java.util.Set;
import java.util.stream.Collectors;

public class RemoveIntrospectionExceptionProcessor extends AbstractProcessor<CtMethod<?>> {
    @Override
    public boolean isToBeProcessed(CtMethod<?> candidate) {
        return "getProperties".equals(candidate.getSimpleName()) || "sortTypeWithOrder".equals(candidate.getSimpleName());
    }

    @Override
    public void process(CtMethod<?> method) {
        Set<CtTypeReference<? extends Throwable>> thrownTypes = method.getThrownTypes();
        Set<CtTypeReference<? extends Throwable>> toKeep = thrownTypes.stream()
                .filter(ref -> !ref.getSimpleName().equals("IntrospectionException"))
                .collect(Collectors.toSet());
        
        if (thrownTypes.size() != toKeep.size()) {
            method.setThrownTypes(toKeep);
            System.out.println("Removed IntrospectionException from " + method.getSimpleName());
        }
    }
}
