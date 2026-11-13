package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtNewClass;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.List;

/**
 * Generic Spoon transformation to fix breaking changes in Jetty dependency updates.
 * Specifically targets the migration from SelectChannelConnector to ServerConnector.
 */
public class Main {
    
    public static void main(String[] args) {
        // Configuration parameters 
        String sourceDirectory = "/workspace/jadler";
        String targetDirectory = "/workspace/jadler-fixed";
        
        // Create the Spoon launcher
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setComplianceLevel(6);
        launcher.addInputResource(sourceDirectory);
        launcher.setSourceOutputDirectory(targetDirectory);
        
        // Build the model
        CtModel model = launcher.buildModel();
        
        // Apply transformations
        fixJettyConnectorIssue(model);
        
        // Process and generate the modified code
        launcher.process();
        
        System.out.println("Transformation completed successfully.");
    }
    
    /**
     * Fix the Jetty connector issue by replacing SelectChannelConnector with ServerConnector
     */
    private static void fixJettyConnectorIssue(CtModel model) {
        // Find all instances of SelectChannelConnector construction
        List<CtNewClass> selectChannelConnectorInstances = model.getElements(
            new TypeFilter<>(CtNewClass.class) {
                @Override
                public boolean matches(CtNewClass element) {
                    return element.getType().getQualifiedName().equals("org.eclipse.jetty.server.nio.SelectChannelConnector");
                }
            });
        
        for (CtNewClass newClass : selectChannelConnectorInstances) {
            // Create a new reference to ServerConnector
            CtTypeReference<?> serverConnectorRef = newClass.getFactory().createReference("org.eclipse.jetty.server.ServerConnector");
            
            // Replace the type reference
            newClass.setType(serverConnectorRef);
        }
    }
}