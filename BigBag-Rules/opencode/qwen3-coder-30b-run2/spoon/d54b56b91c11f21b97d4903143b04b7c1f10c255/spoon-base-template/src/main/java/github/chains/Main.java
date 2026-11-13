package github.chains;

import spoon.Launcher;
import spoon.SpoonAPI;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.factory.Factory;
import spoon.reflect.visitor.Query;
import spoon.reflect.visitor.filter.AbstractFilter;

import java.util.List;

/**
 * Generic Spoon transformation to fix breaking changes in Google Cloud PubSubLite
 * Specifically addresses:
 * 1. Removal of PublishMetadata class
 * 2. Removal of setContext method from SinglePartitionPublisherBuilder.Builder
 */
public class Main {
    
    public static void main(String[] args) {
        // Process the pubsublite-kafka-connector project
        Launcher launcher = new Launcher();
        
        // Set the source directory to the project we're fixing
        launcher.addInputResource("/workspace/java-pubsub-group-kafka-connector/src");
        launcher.setSourceOutputDirectory("/workspace/java-pubsub-group-kafka-connector/transformed-src");
        
        // Configure the launcher to use the correct Java version
        launcher.getEnvironment().setComplianceLevel(8);
        
        // Build the model
        SpoonAPI spoon = launcher;
        spoon.buildModel();
        
        Factory factory = spoon.getFactory();
        
        // Fix 1: Remove PublishMetadata imports and replace references with Object
        fixPublishMetadataReferences(factory);
        
        // Fix 2: Remove setContext method calls completely 
        fixSetContextCalls(factory);
        
        // Print the transformed code
        spoon.prettyprint();
        
        System.out.println("Transformation completed successfully.");
    }
    
    /**
     * Fix references to PublishMetadata class that no longer exists in the newer API
     * Replace with Object in generic contexts to maintain compilation compatibility
     */
    private static void fixPublishMetadataReferences(Factory factory) {
        // Find all imports of PublishMetadata
        List<CtImport> importsToRemove = Query.getElements(factory, new AbstractFilter<CtImport>() {
            @Override
            public boolean matches(CtImport element) {
                return element.getReference() != null &&
                       element.getReference().getSimpleName().equals("PublishMetadata");
            }
        });
        
        // Remove the imports
        for (CtImport imp : importsToRemove) {
            imp.delete();
        }
        
        // Find all references to PublishMetadata in type declarations and method signatures
        List<CtTypeReference<?>> publishMetadataRefs = Query.getElements(factory, new AbstractFilter<CtTypeReference<?>>() {
            @Override
            public boolean matches(CtTypeReference<?> element) {
                return element.getSimpleName().equals("PublishMetadata");
            }
        });
        
        // Replace PublishMetadata with Object (in generic contexts) 
        for (CtTypeReference<?> ref : publishMetadataRefs) {
            // Replace with Object for generic type parameters
            ref.setSimpleName("Object");
        }
    }
    
    /**
     * Fix calls to setContext method that no longer exists in SinglePartitionPublisherBuilder.Builder
     * Simply remove the setContext call since it's no longer available in the new API
     */
    private static void fixSetContextCalls(Factory factory) {
        // Find all invocations of setContext method by searching for method name
        List<CtInvocation<?>> setContextInvocations = Query.getElements(factory, new AbstractFilter<CtInvocation<?>>() {
            @Override
            public boolean matches(CtInvocation<?> element) {
                // Check if it's a method call with the name "setContext"
                return element.getExecutable() != null && 
                       element.getExecutable().getSimpleName().equals("setContext");
            }
        });
        
        // Remove setContext method calls completely
        for (CtInvocation<?> invocation : setContextInvocations) {
            // For this specific case, we'll remove the entire invocation including the setContext method call
            // This is safe because setContext() was part of a builder chain and is no longer needed
            invocation.delete();
        }
    }
}