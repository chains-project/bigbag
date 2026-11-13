package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.List;

/**
 * Generic Spoon transformation to fix Dropwizard Apache Connector constructor calls
 * that were changed in Dropwizard 4.0.0
 * 
 * This transformation addresses the breaking change where DropwizardApacheConnector
 * changed from 2 parameters to 3 parameters in version 4.0.0
 * 
 * Old API: new DropwizardApacheConnector(client, config)
 * New API: new DropwizardApacheConnector(client, config, false)
 */
public class Main {
    public static void main(String[] args) {
        // Process the lithium project
        Launcher launcher = new Launcher();
        launcher.addInputResource("/workspace/lithium/src");
        launcher.setSourceOutputDirectory("/workspace/lithium/src");
        
        CtModel model = launcher.buildModel();
        
        // Find all constructor calls that match DropwizardApacheConnector pattern
        List<CtConstructorCall<?>> constructors = model.getElements(new TypeFilter<>(CtConstructorCall.class));
        
        for (CtConstructorCall<?> constructor : constructors) {
            // Check if this is a DropwizardApacheConnector constructor call with 2 parameters
            if (constructor.getType().getSimpleName().equals("DropwizardApacheConnector")) {
                // Get the constructor reference
                CtExecutableReference<?> execRef = constructor.getExecutable();
                if (execRef != null && execRef.getDeclaringType() != null) {
                    String declaringTypeName = execRef.getDeclaringType().getQualifiedName();
                    // Match the specific class name pattern
                    if (declaringTypeName.equals("io.dropwizard.client.DropwizardApacheConnector")) {
                        // Check if it has exactly 2 arguments (old API) instead of 3 (new API)
                        if (constructor.getArguments().size() == 2) {
                            // Inject the missing boolean parameter (default to false)
                            constructor.addArgument(
                                launcher.getFactory().createLiteral(false)
                            );
                        }
                    }
                }
            }
        }
        
        // Write the transformed code back
        launcher.setSourceOutputDirectory("/workspace/lithium/src");
        launcher.process();
        
        System.out.println("Transformation completed successfully!");
        System.out.println("All DropwizardApacheConnector constructor calls with 2 parameters have been updated to 3 parameters.");
    }
}