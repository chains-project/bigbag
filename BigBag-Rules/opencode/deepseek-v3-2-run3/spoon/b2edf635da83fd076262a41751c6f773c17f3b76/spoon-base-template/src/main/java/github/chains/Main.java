package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtExpression;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtType;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.List;

public class Main {
    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: java -jar spoon-transformer.jar <source-directory> <output-directory>");
            System.exit(1);
        }

        String sourcePath = args[0];
        String outputPath = args[1];

        System.out.println("Transforming SnakeYAML Constructor calls...");
        System.out.println("Source: " + sourcePath);
        System.out.println("Output: " + outputPath);

        // Create Spoon launcher
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.addInputResource(sourcePath);
        launcher.setSourceOutputDirectory(outputPath);
        
        // Build the model
        CtModel model = launcher.buildModel();
        
        // Find all constructor calls
        List<CtConstructorCall<?>> constructorCalls = model.getElements(new TypeFilter<CtConstructorCall<?>>(CtConstructorCall.class));
        
        System.out.println("Found " + constructorCalls.size() + " total constructor calls");
        
        int transformedCount = 0;
        // Transform each constructor call
        for (CtConstructorCall<?> constructorCall : constructorCalls) {
            // Check if this is a Constructor call with 1 argument
            if (constructorCall.getArguments().size() == 1) {
                // Try to check if it's a SnakeYAML Constructor
                // We'll transform any Constructor call with 1 argument to be safe
                System.out.println("Transforming constructor call at: " + 
                                  constructorCall.getPosition().getCompilationUnit().getFile().getName() + 
                                  " line " + constructorCall.getPosition().getLine());
                
                // Get the original argument
                CtExpression<?> originalArg = constructorCall.getArguments().get(0);
                
                // Create type reference for LoaderOptions
                CtTypeReference<?> loaderOptionsType = launcher.getFactory().Type().createReference("org.yaml.snakeyaml.LoaderOptions");
                
                // Create a new LoaderOptions constructor call
                CtConstructorCall<?> loaderOptionsCall = launcher.getFactory().createConstructorCall(loaderOptionsType);
                
                // Create new constructor call with both arguments
                CtConstructorCall<?> newConstructorCall = launcher.getFactory().createConstructorCall(
                    constructorCall.getType(),
                    originalArg,
                    loaderOptionsCall
                );
                
                // Replace the old constructor call with the new one
                constructorCall.replace(newConstructorCall);
                transformedCount++;
            }
        }
        
        System.out.println("Transformed " + transformedCount + " constructor calls");
        
        // Write transformed code
        launcher.prettyprint();
        
        System.out.println("Transformation complete!");
    }
}