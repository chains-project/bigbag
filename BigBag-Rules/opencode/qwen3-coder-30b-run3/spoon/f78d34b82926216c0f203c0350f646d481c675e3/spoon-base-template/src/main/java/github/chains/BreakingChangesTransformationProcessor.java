package github.chains;

import spoon.processing.AbstractProcessor;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.reference.CtTypeReference;

public class BreakingChangesTransformationProcessor extends AbstractProcessor<CtElement> {

    @Override
    public void process(CtElement element) {
        // Try to handle package changes by recreating type references
        if (element instanceof CtTypeReference<?> typeRef) {
            String qualifiedName = typeRef.getQualifiedName();
            
            // If it's a reference to the old package, replace it with the new one
            if (qualifiedName.startsWith("develop.p2p.lib.")) {
                // Create new reference with corrected package
                String newQualifiedName = qualifiedName.replace("develop.p2p.lib.", "tokyo.peya.lib.");
                
                // Create a new type reference to replace the old one
                CtTypeReference<?> newTypeRef = typeRef.getFactory().createTypeReference(newQualifiedName);
                
                // Replace the old reference with the new one
                typeRef.replace(newTypeRef);
            }
        }
    }
}