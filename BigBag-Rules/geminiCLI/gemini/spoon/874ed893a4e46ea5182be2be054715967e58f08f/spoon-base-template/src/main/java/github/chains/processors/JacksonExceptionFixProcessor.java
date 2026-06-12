package github.chains.processors;

import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtCatch;
import spoon.reflect.code.CtCatchVariable;
import spoon.reflect.reference.CtTypeReference;

public class JacksonExceptionFixProcessor extends AbstractProcessor<CtCatch> {
    @Override
    public void process(CtCatch element) {
        CtCatchVariable<?> parameter = element.getParameter();
        CtTypeReference<?> type = parameter.getType();
        if (type.getQualifiedName().equals("com.fasterxml.jackson.core.JsonProcessingException")) {
            CtTypeReference<?> newType = getFactory().Type().createReference("com.fasterxml.jackson.core.JacksonException");
            parameter.setType((CtTypeReference) newType);
        }
    }
}
