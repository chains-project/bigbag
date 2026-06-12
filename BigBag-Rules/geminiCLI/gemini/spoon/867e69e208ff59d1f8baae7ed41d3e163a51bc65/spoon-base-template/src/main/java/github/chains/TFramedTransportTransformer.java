package github.chains;

import spoon.processing.AbstractProcessor;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.reference.CtTypeReference;

public class TFramedTransportTransformer extends AbstractProcessor<CtElement> {
    @Override
    public void process(CtElement element) {
        if (element instanceof CtImport) {
             CtImport ctImport = (CtImport) element;
             if (ctImport.getReference() instanceof CtTypeReference) {
                 CtTypeReference<?> ref = (CtTypeReference<?>) ctImport.getReference();
                 if ("org.apache.thrift.transport.TFramedTransport".equals(ref.getQualifiedName())) {
                     ctImport.delete();
                 }
             }
        } else if (element instanceof CtTypeReference) {
            CtTypeReference<?> ref = (CtTypeReference<?>) element;
            if ("org.apache.thrift.transport.TFramedTransport".equals(ref.getQualifiedName())) {
                ref.setPackage(getFactory().Package().getOrCreate("org.apache.thrift.transport.layered").getReference());
            }
        }
    }
}
