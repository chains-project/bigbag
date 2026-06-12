package github.chains;

import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtExpression;
import java.util.List;

public class HamcrestConstructorFixer extends AbstractProcessor<CtConstructorCall<?>> {
    @Override
    public boolean isToBeProcessed(CtConstructorCall<?> candidate) {
        if (candidate.getExecutable() == null || candidate.getExecutable().getDeclaringType() == null) {
            return false;
        }
        String typeName = candidate.getExecutable().getDeclaringType().getQualifiedName();
        return (typeName.equals("org.hamcrest.core.StringContains") || typeName.equals("org.hamcrest.core.StringStartsWith"))
                && candidate.getArguments().size() == 2;
    }

    @Override
    public void process(CtConstructorCall<?> element) {
        List<CtExpression<?>> args = element.getArguments();
        if (args.size() == 2) {
            CtExpression<?> firstArg = args.get(0);
            // Simple check for boolean literal or type
            // In noclasspath, type might be unknown, but toString() of literal "true" or "false" works.
            String argText = firstArg.toString();
            if (argText.equals("true") || argText.equals("false") || 
                (firstArg.getType() != null && firstArg.getType().getSimpleName().equalsIgnoreCase("boolean"))) {
                
                element.removeArgument(firstArg);
                System.out.println("Fixed: " + element.getPosition());
            }
        }
    }
}
