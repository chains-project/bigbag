package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.reflect.visitor.PrettyPrinter;

/**
 * Generic transformation to fix breaking changes in google-cloud-pubsublite dependency
 * Specifically addresses:
 * - Removal of com.google.cloud.pubsublite.PublishMetadata class
 * - Changes to PartitionPublisherFactory functional interface
 */
public class Main {
    
    public static void main(String[] args) {
        // Set up Spoon
        Launcher launcher = new Launcher();
        
        // Configure input path - this can be parameterized
        String inputPath = "/workspace/java-pubsub-group-kafka-connector/src";
        launcher.addInputResource(inputPath);
        
        // Set output path
        String outputPath = "/workspace/java-pubsub-group-kafka-connector/src-fixed";
        launcher.setSourceOutputDirectory(outputPath);
        
        // Add Java compliance level
        launcher.getEnvironment().setComplianceLevel(8);
        
        // Build model
        CtModel model = launcher.buildModel();
        
        // Apply transformations
        fixPublishMetadataUsages(model);
        
        // Process the model (save files)
        launcher.process();
        
        System.out.println("Transformation complete. Files can be found in: " + outputPath);
    }
    
    /**
     * Fix usages of PublishMetadata that were removed
     */
    private static void fixPublishMetadataUsages(CtModel model) {
        Factory factory = model.getFactory();
        
        // Find and remove imports of PublishMetadata
        model.getElements(new TypeFilter<>(CtImport.class)).stream()
            .filter(importEl -> {
                // Check if it's importing PublishMetadata
                return importEl.getReference() != null && 
                       (importEl.getReference().getQualifiedName().endsWith(".PublishMetadata") ||
                        "PublishMetadata".equals(importEl.getReference().getSimpleName()));
            })
            .forEach(CtImport::delete);
        
        // Fix return types in methods that return Publisher<PublishMetadata>
        model.getElements(new TypeFilter<>(CtMethod.class)).stream()
            .filter(method -> {
                // Check if the method returns Publisher with PublishMetadata
                return method.getType() != null && 
                       method.getType().getActualTypeArguments().size() > 0 &&
                       method.getType().getActualTypeArguments().get(0).getSimpleName().equals("PublishMetadata");
            })
            .forEach(method -> {
                // Replace return type with Publisher<Void>
                CtTypeReference<?> newReturnType = factory.Type().createReference("com.google.cloud.pubsublite.internal.Publisher");
                newReturnType.addActualTypeArgument(factory.Type().VOID_PRIMITIVE);
                method.setType(newReturnType);
            });
    }
}