package github.chains;

import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.code.CtExpression;
import java.util.List;

public class TransportFixProcessor extends AbstractProcessor<CtElement> {

    @Override
    public boolean isToBeProcessed(CtElement candidate) {
        if (candidate instanceof CtTypeReference) {
            return "TFastFramedTransport".equals(((CtTypeReference<?>) candidate).getSimpleName());
        }
        if (candidate instanceof CtConstructorCall) {
            String name = ((CtConstructorCall<?>) candidate).getType().getSimpleName();
            return "TFastFramedTransport".equals(name) || "TFramedTransport".equals(name);
        }
        return false;
    }

    @Override
    public void process(CtElement element) {
        if (element instanceof CtTypeReference) {
            CtTypeReference<?> ref = (CtTypeReference<?>) element;
            if ("TFastFramedTransport".equals(ref.getSimpleName())) {
                ref.setSimpleName("TFramedTransport");
            }
        } else if (element instanceof CtConstructorCall) {
            CtConstructorCall<?> call = (CtConstructorCall<?>) element;
            List<CtExpression<?>> args = call.getArguments();
            // We specifically want to remove the second argument if it was the TFastFramedTransport call (which has 2 args)
            // validating it is indeed the transport constructor we are targeting.
            if (args.size() == 2) {
                // Check first arg type if possible, or just assume it's the right one since TFastFramedTransport is rare
                call.removeArgument(args.get(1));
            }
        }
    }
}
