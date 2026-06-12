package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.factory.Factory;
import spoon.reflect.visitor.filter.TypeFilter;
import java.util.List;

/**
 * Generic Spoon transformation to fix the PublishMetadata removal issue.
 * This transformation addresses the breaking change where PublishMetadata
 * was removed from com.google.cloud.pubsublite in version 1.6.3.
 */
public class Main {
    public static void main(String[] args) {
        // Create a Spoon launcher
        Launcher launcher = new Launcher();
        
        // Set the source folder
        launcher.addInputResource("/workspace/java-pubsub-group-kafka-connector/src");
        
        // Set the output folder
        launcher.setSourceOutputDirectory("/workspace/java-pubsub-group-kafka-connector/src");
        
        // Build the model
        CtModel model = launcher.buildModel();
        Factory factory = launcher.getFactory();
        
        // Find and remove imports of com.google.cloud.pubsublite.PublishMetadata
        List<CtImport> imports = model.getElements(new TypeFilter<>(CtImport.class));
        for (CtImport imp : imports) {
            if (imp.getReference() != null && 
                "com.google.cloud.pubsublite.PublishMetadata".equals(imp.getReference().getQualifiedName())) {
                imp.delete();
            }
        }
        
        // Find all type references to Publisher<PublishMetadata> and replace with Publisher<Message>
        List<CtTypeReference<?>> typeReferences = model.getElements(new TypeFilter<>(CtTypeReference.class));
        for (CtTypeReference<?> typeRef : typeReferences) {
            // Check if this is a Publisher with PublishMetadata as generic parameter
            if ("com.google.cloud.pubsublite.internal.Publisher".equals(typeRef.getQualifiedName())) {
                List<CtTypeReference<?>> actualTypeArguments = typeRef.getActualTypeArguments();
                if (actualTypeArguments.size() > 0) {
                    CtTypeReference<?> firstArg = actualTypeArguments.get(0);
                    if (firstArg != null && 
                        "com.google.cloud.pubsublite.PublishMetadata".equals(firstArg.getQualifiedName())) {
                        
                        // Replace with Publisher<Message>
                        CtTypeReference<?> messageType = factory.Type().createReference("com.google.cloud.pubsublite.Message");
                        typeRef.setActualTypeArguments(List.of(messageType));
                    }
                }
            }
        }
        
        // Write the modified code back to files
        launcher.setSourceOutputDirectory("/workspace/java-pubsub-group-kafka-connector/src");
        launcher.process();
        
        System.out.println("Transformation completed successfully!");
    }
}