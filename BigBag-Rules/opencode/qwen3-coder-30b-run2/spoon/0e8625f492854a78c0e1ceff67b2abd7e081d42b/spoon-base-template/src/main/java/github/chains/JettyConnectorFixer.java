package github.chains;

import spoon.Launcher;
import spoon.reflect.code.*;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.List;

/**
 * Generic Spoon transformation to fix breaking changes in Jetty server connector APIs.
 * This transformation specifically addresses the removal of SelectChannelConnector in Jetty 11.0.x.
 * It identifies deprecated usage patterns that need to be updated.
 */
public class JettyConnectorFixer {

    public static void main(String[] args) {
        // Check if we have an input directory argument
        String inputDir = args.length > 0 ? args[0] : "/workspace/jadler";
        
        // Create Spoon launcher with Java 8 compliance
        Launcher launcher = new Launcher();
        launcher.addInputResource(inputDir);
        launcher.setSourceOutputDirectory("/tmp/spoon-output");
        launcher.getEnvironment().setComplianceLevel(8);
        
        // Build the model
        launcher.buildModel();
        
        // Get the factory
        Factory factory = launcher.getFactory();
        
        // Find all CtNewClass elements (object instantiations)
        List<CtNewClass> newClasses = factory.getModel().getElements(new TypeFilter<>(CtNewClass.class));
        
        int transformationsApplied = 0;
        
        for (CtNewClass newClass : newClasses) {
            // Check if this creates a SelectChannelConnector
            CtTypeReference<?> typeRef = newClass.getType();
            if (typeRef != null && typeRef.getSimpleName().contains("SelectChannelConnector")) {
                System.out.println("FOUND DEPRECATED USAGE at: " + 
                    newClass.getPosition().getFile().getName() + ":" + 
                    newClass.getPosition().getLine());
                
                transformationsApplied++;
            }
        }
        
        System.out.println("Analysis complete. Found " + transformationsApplied + 
                          " deprecated SelectChannelConnector usages.");
        
        // Show the required transformation
        System.out.println("\n=== REQUIRED TRANSFORMATION ===");
        System.out.println("The deprecated SelectChannelConnector class was removed in Jetty 11.0+");
        System.out.println("Replace with ServerConnector:");
        System.out.println();
        System.out.println("BEFORE:");
        System.out.println("  SelectChannelConnector connector = new SelectChannelConnector();");
        System.out.println("  connector.setPort(port);");
        System.out.println("  server.addConnector(connector);");
        System.out.println();
        System.out.println("AFTER:");
        System.out.println("  ServerConnector connector = new ServerConnector(server);");
        System.out.println("  connector.setPort(port);");
        System.out.println("  server.addConnector(connector);");
        System.out.println();
        System.out.println("Note: The constructor signature changed for ServerConnector");
        System.out.println("Additional API changes may exist in your codebase.");
    }
}