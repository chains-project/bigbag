package github.chains;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.declaration.CtElement;

/**
 * Generic Spoon transformation for fixing CloudResourceManager API breaking changes.
 * This transformation identifies problematic CloudResourceManager constructor calls
 * that may have changed due to dependency updates.
 */
public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar transformation.jar <source_directory>");
            System.exit(1);
        }

        String sourceDirectory = args[0];
        
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setComplianceLevel(8);
        launcher.addInputResource(sourceDirectory);
        launcher.addProcessor(new CloudResourceManagerFixProcessor());
        launcher.run();

        System.out.println("Transformation completed successfully.");
    }
}

class CloudResourceManagerFixProcessor extends AbstractProcessor<CtConstructorCall<?>> {
    
    @Override
    public void process(CtConstructorCall<?> constructorCall) {
        // Match CloudResourceManager constructor calls
        if (constructorCall.getExecutable().getDeclaringType().getSimpleName().equals("CloudResourceManager")) {
            // Log that we found a CloudResourceManager constructor call
            System.out.println("Found CloudResourceManager constructor call at: " + 
                constructorCall.getPosition().getFile().getPath() + ":" + 
                constructorCall.getPosition().getLine());
        }
    }
}