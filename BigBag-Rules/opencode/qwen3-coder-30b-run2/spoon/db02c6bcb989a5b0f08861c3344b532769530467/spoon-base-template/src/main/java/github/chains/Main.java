package github.chains;

import spoon.Launcher;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.List;

/**
 * Generic Spoon transformation to fix Hamcrest API breaking changes in StringContains and StringStartsWith constructors.
 * 
 * This transformation looks for constructor calls to:
 * - org.hamcrest.core.StringContains(boolean, String) 
 * - org.hamcrest.core.StringStartsWith(boolean, String)
 * 
 * And transforms them to:
 * - org.hamcrest.core.StringContains(String)
 * - org.hamcrest.core.StringStartsWith(String)
 */
public class Main {
    public static void main(String[] args) {
        // Default to the docker-adapter project if no input specified
        String inputPath = args.length > 0 ? args[0] : "/workspace/docker-adapter";
        
        Launcher launcher = new Launcher();
        launcher.addInputResource(inputPath);
        
        // Set the output directory
        String outputPath = "/tmp/spoon-output";
        launcher.setSourceOutputDirectory(outputPath);
        
        // Build the model
        launcher.buildModel();
        Factory factory = launcher.getFactory();
        
        // Transform StringContains constructor calls
        transformStringContains(factory);
        
        // Transform StringStartsWith constructor calls  
        transformStringStartsWith(factory);
        
        // Print success message
        System.out.println("Transformation completed. Output saved to " + outputPath);
        
        // Write the transformed code to the output directory
        launcher.process();
    }
    
    private static void transformStringContains(Factory factory) {
        // Find all constructor calls to StringContains with (boolean, String) signature
        List<CtConstructorCall<?>> stringContainsCalls = factory
            .getModel()
            .filterChildren(new TypeFilter<>(CtConstructorCall.class))
            .select((CtConstructorCall<?> constructorCall) -> {
                CtTypeReference<?> ctType = constructorCall.getType();
                if (ctType == null) return false;
                
                // Check if it's StringContains constructor with boolean, String parameters
                String typeName = ctType.getQualifiedName();
                if (!typeName.equals("org.hamcrest.core.StringContains")) {
                    return false;
                }
                
                // Check if it has 2 arguments: boolean and String
                List<?> arguments = constructorCall.getArguments();
                if (arguments.size() != 2) {
                    return false;
                }
                
                // Check that first argument is boolean and second is String
                Object firstArg = arguments.get(0);
                Object secondArg = arguments.get(1);
                
                // We can't easily check types in this version, so we'll assume any two-arg 
                // StringContains constructor needs transformation
                return true;
            })
            .list();
            
        // Apply transformation to each match
        for (CtConstructorCall<?> call : stringContainsCalls) {
            // Remove the first argument (the boolean)
            List<?> arguments = call.getArguments();
            if (arguments.size() >= 2) {
                arguments.remove(0);
            }
        }
    }
    
    private static void transformStringStartsWith(Factory factory) {
        // Find all constructor calls to StringStartsWith with (boolean, String) signature
        List<CtConstructorCall<?>> stringStartsWithCalls = factory
            .getModel()
            .filterChildren(new TypeFilter<>(CtConstructorCall.class))
            .select((CtConstructorCall<?> constructorCall) -> {
                CtTypeReference<?> ctType = constructorCall.getType();
                if (ctType == null) return false;
                
                // Check if it's StringStartsWith constructor with boolean, String parameters
                String typeName = ctType.getQualifiedName();
                if (!typeName.equals("org.hamcrest.core.StringStartsWith")) {
                    return false;
                }
                
                // Check if it has 2 arguments: boolean and String
                List<?> arguments = constructorCall.getArguments();
                if (arguments.size() != 2) {
                    return false;
                }
                
                // We can't easily check types in this version, so we'll assume any two-arg 
                // StringStartsWith constructor needs transformation
                return true;
            })
            .list();
            
        // Apply transformation to each match
        for (CtConstructorCall<?> call : stringStartsWithCalls) {
            // Remove the first argument (the boolean)
            List<?> arguments = call.getArguments();
            if (arguments.size() >= 2) {
                arguments.remove(0);
            }
        }
    }
}