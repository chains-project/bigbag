package github.chains;

import spoon.Launcher;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtLiteral;
import spoon.reflect.factory.Factory;

public class Main {
    public static void main(String[] args) {
        // Create a Spoon launcher
        Launcher launcher = new Launcher();
        
        // Set the source directory to the lithium project
        launcher.addInputResource("/workspace/lithium/src");
        
        // Set the output directory
        launcher.setSourceOutputDirectory("/workspace/spoon-output");
        
        // Build the model
        launcher.buildModel();
        
        // Get the factory
        Factory factory = launcher.getFactory();
        
        // Process all constructor calls in the codebase
        launcher.getModel().getElements(element -> {
            if (element instanceof CtConstructorCall) {
                CtConstructorCall constructorCall = (CtConstructorCall) element;
                String typeName = constructorCall.getType().getQualifiedName();
                
                // Check for DropwizardApacheConnector constructor calls
                if (typeName.contains("DropwizardApacheConnector")) {
                    // If it has 2 arguments, we need to add a third boolean argument
                    if (constructorCall.getArguments().size() == 2) {
                        // Add the boolean parameter with default value false
                        CtLiteral<Boolean> falseLiteral = factory.createLiteral(false);
                        constructorCall.addArgument(falseLiteral);
                    }
                }
            }
            return true;
        });
        
        // Write the modified code to output directory
        launcher.setSourceOutputDirectory("/workspace/spoon-output");
        launcher.process();
        
        System.out.println("Transformation completed for Dropwizard client 4.0.0 compatibility");
    }
}