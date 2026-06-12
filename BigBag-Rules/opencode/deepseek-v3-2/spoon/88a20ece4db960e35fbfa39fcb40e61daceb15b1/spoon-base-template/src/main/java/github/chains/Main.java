package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;

public class Main {
    public static void main(String[] args) {
        if (args.length < 2) {
            printUsage();
            System.exit(1);
        }
        
        String sourceDir = args[0];
        String outputDir = args[1];
        
        System.out.println("Transforming source directory: " + sourceDir);
        System.out.println("Output directory: " + outputDir);
        
        // Create Spoon launcher
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.addInputResource(sourceDir);
        launcher.setSourceOutputDirectory(outputDir);
        
        // Build the model
        CtModel model = launcher.buildModel();
        
        // Apply transformation for the Pub/Sub Lite breaking change
        ConfigurableTypeReplacer replacer = ConfigurableTypeReplacer.createForPubsubliteBreakingChange(launcher);
        replacer.transform(model);
        
        // Provide guidance for other breaking changes
        printGuidance();
        
        // Write transformed code
        launcher.prettyprint();
        
        System.out.println("\nTransformation complete!");
    }
    
    private static void printUsage() {
        System.err.println("Usage: java -jar spoon-transformer.jar <sourceDir> <outputDir>");
        System.err.println("Example: java -jar spoon-transformer.jar /path/to/src /path/to/transformed");
        System.err.println("\nThis transformation fixes breaking changes in dependencies.");
        System.err.println("For google-cloud-pubsublite 1.6.3, it fixes:");
        System.err.println("1. PublishMetadata -> MessageMetadata (type rename)");
        System.err.println("\nOther breaking changes may require manual fixes:");
        System.err.println("- PartitionPublisherFactory is no longer a functional interface");
        System.err.println("  (lambdas need to be converted to anonymous classes)");
    }
    
    private static void printGuidance() {
        System.out.println("\nADDITIONAL GUIDANCE:");
        System.out.println("=========================================");
        System.out.println("1. PartitionPublisherFactory lambda expressions:");
        System.out.println("   The interface now has 2 abstract methods (newPublisher and close),");
        System.out.println("   so lambdas can't implement it directly anymore.");
        System.out.println("   Example fix - convert 'partition -> ...' to:");
        System.out.println("   new PartitionPublisherFactory() {");
        System.out.println("       @Override");
        System.out.println("       public Publisher<MessageMetadata> newPublisher(Partition partition) { ... }");
        System.out.println("       @Override");
        System.out.println("       public void close() { }");
        System.out.println("   }");
        System.out.println("\n2. Check for other compilation errors:");
        System.out.println("   After applying this transformation, compile the project");
        System.out.println("   to identify any remaining issues.");
        System.out.println("=========================================");
    }
}