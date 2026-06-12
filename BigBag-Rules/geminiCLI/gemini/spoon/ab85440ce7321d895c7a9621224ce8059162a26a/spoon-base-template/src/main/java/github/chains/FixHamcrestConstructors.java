package github.chains;

import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtExpression;
import java.util.List;

public class FixHamcrestConstructors extends AbstractProcessor<CtConstructorCall<?>> {
    @Override
    public void process(CtConstructorCall<?> ctConstructorCall) {
        String typeName = ctConstructorCall.getType().getQualifiedName();
        if ("org.hamcrest.core.StringContains".equals(typeName) || "org.hamcrest.core.StringStartsWith".equals(typeName)) {
            List<CtExpression<?>> arguments = ctConstructorCall.getArguments();
            if (arguments.size() == 2) {
                // Remove the first argument (which is the boolean)
                ctConstructorCall.removeArgument(arguments.get(0));
            }
        }
    }
}
