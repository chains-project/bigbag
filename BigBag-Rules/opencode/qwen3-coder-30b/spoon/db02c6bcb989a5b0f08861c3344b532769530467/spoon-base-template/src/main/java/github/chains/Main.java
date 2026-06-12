package github.chains;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtLiteral;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtTypeReference;

public class Main {
    public static void main(String[] args) {
        // This is a generic Spoon transformation to fix Hamcrest API breaking changes
        // where StringContains and StringStartsWith constructors changed from:
        // new StringContains(boolean, String) to new StringContains(String)
        // and StringStartsWith(boolean, String) to StringStartsWith(String)
        
        // Create a launcher for Spoon
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        
        // Add a processor to fix the Hamcrest constructor calls
        launcher.addProcessor(new AbstractProcessor<CtConstructorCall<?>>() {
            @Override
            public void process(CtConstructorCall<?> constructorCall) {
                // Check if this is a constructor call to StringContains with two parameters
                if (constructorCall.getExecutable().getSimpleName().equals("StringContains") && 
                    constructorCall.getExecutable().getDeclaringType().getQualifiedName().equals("org.hamcrest.core.StringContains") &&
                    constructorCall.getArguments().size() == 2) {
                    
                    // Get the second argument (the string parameter) - this is what we want to keep
                    CtElement stringArg = constructorCall.getArguments().get(1);
                    
                    // Create a new constructor call with only the string parameter
                    CtConstructorCall<?> newConstructorCall = constructorCall.getFactory().Code().createConstructorCall(
                        constructorCall.getExecutable().getDeclaringType().getReference()
                    );
                    
                    // Add the string argument (the second argument from the original call)
                    newConstructorCall.addArgument(stringArg);
                    
                    // Replace the old constructor call
                    constructorCall.replace(newConstructorCall);
                }
                
                // Check if this is a constructor call to StringStartsWith with two parameters
                if (constructorCall.getExecutable().getSimpleName().equals("StringStartsWith") && 
                    constructorCall.getExecutable().getDeclaringType().getQualifiedName().equals("org.hamcrest.core.StringStartsWith") &&
                    constructorCall.getArguments().size() == 2) {
                    
                    // Get the second argument (the string parameter) - this is what we want to keep
                    CtElement stringArg = constructorCall.getArguments().get(1);
                    
                    // Create a new constructor call with only the string parameter
                    CtConstructorCall<?> newConstructorCall = constructorCall.getFactory().Code().createConstructorCall(
                        constructorCall.getExecutable().getDeclaringType().getReference()
                    );
                    
                    // Add the string argument (the second argument from the original call)
                    newConstructorCall.addArgument(stringArg);
                    
                    // Replace the old constructor call
                    constructorCall.replace(newConstructorCall);
                }
            }
        });
        
        // Process the input source code
        launcher.setSourceInput("/workspace/docker-adapter/src");
        launcher.buildModel();
        
        // Write the transformed code back
        launcher.getEnvironment().setShouldGenerateSource(true);
        launcher.getEnvironment().setSourceOutputDirectory("/workspace/docker-adapter/src");
        launcher.process();
        
        System.out.println("Hamcrest API transformation completed successfully!");
    }
}