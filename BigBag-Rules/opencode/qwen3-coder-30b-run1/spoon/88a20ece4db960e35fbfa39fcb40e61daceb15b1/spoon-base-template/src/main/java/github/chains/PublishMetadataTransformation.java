package github.chains;

import spoon.processing.AbstractProcessor;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.declaration.CtType;
import spoon.reflect.reference.CtTypeReference;
import spoon.support.reflect.declaration.CtImportImpl;

/**
 * Generic Spoon transformation to handle the removal of PublishMetadata class from
 * com.google.cloud.pubsublite in version 1.6.3.
 * 
 * This transformation:
 * 1. Removes imports of com.google.cloud.pubsublite.PublishMetadata
 * 2. Replaces Publisher<PublishMetadata> with Publisher<Message> (based on API analysis)
 */
public class PublishMetadataTransformation extends AbstractProcessor<CtType<?>> {
    
    @Override
    public void process(CtType<?> type) {
        // Remove PublishMetadata imports
        for (CtImport imp : type.getImports()) {
            if (imp.getImportedType() != null && 
                "com.google.cloud.pubsublite.PublishMetadata".equals(imp.getImportedType().getQualifiedName())) {
                imp.delete();
            }
        }
        
        // Replace Publisher<PublishMetadata> with Publisher<Message>
        if (type.getTypeReferences() != null) {
            for (CtTypeReference<?> typeRef : type.getTypeReferences()) {
                if (typeRef.getQualifiedName().equals("com.google.cloud.pubsublite.internal.Publisher") &&
                    typeRef.getTypeArguments() != null &&
                    typeRef.getTypeArguments().size() == 1) {
                    
                    CtTypeReference<?> arg = typeRef.getTypeArguments().get(0);
                    if (arg.getQualifiedName().equals("com.google.cloud.pubsublite.PublishMetadata")) {
                        // Replace PublishMetadata with Message
                        typeRef.getTypeArguments().set(0, type.getFactory().Type().createReference("com.google.cloud.pubsublite.Message"));
                    }
                }
            }
        }
    }
}