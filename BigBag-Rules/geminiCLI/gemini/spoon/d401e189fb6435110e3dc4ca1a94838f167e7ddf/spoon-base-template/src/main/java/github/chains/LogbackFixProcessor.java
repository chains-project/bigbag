package github.chains;

import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtType;

import java.util.Arrays;
import java.util.List;

public class LogbackFixProcessor extends AbstractProcessor<CtInvocation<?>> {
    
    private static final List<String> TARGET_CLASSES = Arrays.asList(
            "com.feedzai.commons.sql.abstraction.engine.impl.abs.EngineCloseTest",
            "com.feedzai.commons.sql.abstraction.engine.impl.abs.EngineGeneralTest",
            "com.feedzai.commons.sql.abstraction.engine.impl.abs.BatchUpdateTest"
    );

    @Override
    public boolean isToBeProcessed(CtInvocation<?> candidate) {
        if (!"setLevel".equals(candidate.getExecutable().getSimpleName())) {
            return false;
        }

        CtType<?> parentType = candidate.getParent(CtType.class);
        if (parentType == null || !TARGET_CLASSES.contains(parentType.getQualifiedName())) {
            return false;
        }

        return true;
    }

    @Override
    public void process(CtInvocation<?> element) {
        CtMethod<?> method = element.getParent(CtMethod.class);
        if (method != null && "initStatic".equals(method.getSimpleName())) {
             method.setBody(getFactory().Core().createBlock());
             System.out.println("Emptied initStatic body in " + method.getParent(CtType.class).getSimpleName());
        } else {
             element.getParent(spoon.reflect.code.CtStatement.class).delete();
             System.out.println("Deleted setLevel statement in " + element.getParent(CtType.class).getSimpleName());
        }
    }
}
