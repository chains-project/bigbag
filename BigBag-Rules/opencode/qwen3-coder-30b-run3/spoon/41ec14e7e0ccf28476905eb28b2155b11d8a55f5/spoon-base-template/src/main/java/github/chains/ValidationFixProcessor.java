package github.chains;

import spoon.processing.AbstractProcessor;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.reference.CtReference;
import spoon.reflect.reference.CtTypeReference;

/**
 * Generic transformation to fix breaking changes in pm-wicket-utils 5.0
 * Specifically addresses removal of javax.validation and org.apache.commons.beanutils
 */
public class ValidationFixProcessor extends AbstractProcessor<CtElement> {

    @Override
    public void process(CtElement element) {
        // Handle imports
        if (element instanceof CtImport) {
            CtImport ctImport = (CtImport) element;
            CtReference reference = ctImport.getReference();
            
            if (reference instanceof CtTypeReference) {
                CtTypeReference<?> typeRef = (CtTypeReference<?>) reference;
                String qualifiedName = typeRef.getQualifiedName();
                
                // Remove all javax.validation imports
                if (qualifiedName.startsWith("javax.validation")) {
                    getFactory().getEnvironment().report(this, element, "Removing deprecated javax.validation import: " + qualifiedName);
                    element.delete();
                }
                
                // Remove org.apache.commons.beanutils.PropertyUtils import
                if (qualifiedName.equals("org.apache.commons.beanutils.PropertyUtils")) {
                    getFactory().getEnvironment().report(this, element, "Removing deprecated org.apache.commons.beanutils.PropertyUtils import");
                    element.delete();
                }
            }
        }
    }
}