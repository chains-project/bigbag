package github.chains;

import java.io.IOException;
import java.util.List;

/**
 * Example usage of the generic transformation for the specific breaking change:
 * - PublishMetadata → MessageMetadata in Google Cloud Pub/Sub Lite 1.6.3
 * - PartitionPublisherFactory is no longer a functional interface
 * 
 * This demonstrates how to use the GenericTransformation class for a specific
 * breaking API change.
 */
public class Main {
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java github.chains.Main <source-directory>");
            System.err.println("");
            System.err.println("Example transformation for Google Cloud Pub/Sub Lite 1.6.3 breaking changes:");
            System.err.println("  - com.google.cloud.pubsublite.PublishMetadata → com.google.cloud.pubsublite.MessageMetadata");
            System.err.println("  - PartitionPublisherFactory is no longer a functional interface");
            System.err.println("    (lambda expressions passed to setPublisherFactory() need conversion)");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        
        // Configure the transformation for the specific breaking change
        String oldTypeFqn = "com.google.cloud.pubsublite.PublishMetadata";
        String newTypeFqn = "com.google.cloud.pubsublite.MessageMetadata";
        List<String> problematicMethods = List.of("setPublisherFactory");
        
        GenericTransformation transformation = new GenericTransformation(
            oldTypeFqn, newTypeFqn, problematicMethods);
        
        try {
            transformation.transform(sourceDir);
        } catch (IOException e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}