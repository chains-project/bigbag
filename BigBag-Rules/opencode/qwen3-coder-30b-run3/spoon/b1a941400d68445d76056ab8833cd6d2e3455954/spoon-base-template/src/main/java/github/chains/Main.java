/*
 * Generic Spoon Transformation Rule for SnakeYAML Constructor Issue
 * 
 * Problem: SnakeYAML 2.0 removed the default Constructor() constructor.
 *          All constructors now require a LoaderOptions parameter.
 * 
 * Solution Pattern: Replace all occurrences of:
 *   new Constructor() 
 * With:
 *   new Constructor(new LoaderOptions())
 * 
 * Usage: 
 * 1. Copy this transformation to your project
 * 2. Modify the input source directory path
 * 3. Run with: java -cp target/classes:$(mvn dependency:build-classpath -q -Dmdep.outputFile=/dev/stdout) github.chains.Main
 * 
 * This transformation is designed to be reusable across any Maven project with the same issue.
 */

package github.chains;

import spoon.Launcher;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.factory.Factory;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.List;

public class Main {
    public static void main(String[] args) {
        System.out.println("Generic SnakeYAML Constructor Transformation");
        System.out.println("=============================================");
        
        // Define source directory - can be overridden by command line argument
        String sourceDir = args.length > 0 ? args[0] : "/workspace/fluxtion";
        System.out.println("Processing source directory: " + sourceDir);
        
        try {
            // Create launcher and configure it
            Launcher launcher = new Launcher();
            launcher.addInputResource(sourceDir);
            launcher.setSourceOutputDirectory("/tmp/transformed_output");
            
            // Build model
            launcher.buildModel();
            
            Factory factory = launcher.getFactory();
            
            // Find Constructor() calls to SnakeYAML's Constructor class
            List<CtConstructorCall<?>> constructorCalls = 
                factory.getModel().getRootPackage()
                    .accept(new spoon.reflect.visitor.chain.CtQueryImpl(factory)
                        .filterChildren(new TypeFilter<>(CtConstructorCall.class))
                        .filterBy((CtConstructorCall<?> call) -> 
                            call.getType().getSimpleName().equals("Constructor") &&
                            call.getArguments().isEmpty() &&
                            call.getType().getQualifiedName().startsWith("org.yaml.snakeyaml.constructor")
                        )
                        .list());
            
            System.out.println("Found " + constructorCalls.size() + " Constructor() calls to fix");
            
            // Apply transformations
            int transformedCount = 0;
            for (CtConstructorCall<?> call : constructorCalls) {
                try {
                    // Create new constructor call with LoaderOptions
                    CtConstructorCall<?> newCall = factory.Core().createConstructorCall();
                    newCall.setType(call.getType());
                    
                    // Create LoaderOptions constructor call
                    CtConstructorCall<?> loaderOptionsCall = factory.Core().createConstructorCall();
                    loaderOptionsCall.setType(factory.Type().createReference("org.yaml.snakeyaml.LoaderOptions"));
                    
                    // Add argument to new constructor call
                    newCall.addArgument(loaderOptionsCall);
                    
                    // Replace original call
                    call.replace(newCall);
                    transformedCount++;
                    
                } catch (Exception e) {
                    System.err.println("Failed to transform constructor call: " + e.getMessage());
                }
            }
            
            // Export transformed code
            launcher.setSourceOutputDirectory("/tmp/transformed_output");
            launcher.run();
            
            System.out.println("Transformation completed successfully!");
            System.out.println("Transformed " + transformedCount + " Constructor() calls");
            
        } catch (Exception e) {
            System.err.println("Error running transformation: " + e.getMessage());
            e.printStackTrace();
        }
    }
}