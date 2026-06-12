package github.chains;

import spoon.processing.AbstractProcessor;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.reference.CtTypeReference;
import java.util.ArrayList;
import java.util.List;

public class RemoveIntrospectionExceptionProcessor extends AbstractProcessor<CtMethod<?>> {
    @Override
    public void process(CtMethod<?> method) {
        if (method.getDeclaringType().getSimpleName().equals("ModelRepresenter") &&
            (method.getSimpleName().equals("getProperties") || method.getSimpleName().equals("sortTypeWithOrder"))) {
            
            List<CtTypeReference<? extends Throwable>> toRemove = new ArrayList<>();
            for (CtTypeReference<? extends Throwable> thrownType : method.getThrownTypes()) {
                if (thrownType.getSimpleName().equals("IntrospectionException")) {
                    toRemove.add(thrownType);
                }
            }
            
            for (CtTypeReference<? extends Throwable> type : toRemove) {
                method.removeThrownType(type);
            }
        }
    }
}
