package github.chains;

import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.List;

/**
 * Generic Spoon transformation for fixing breaking API changes in dependencies.
 * This transformation handles the case where a constructor signature changed from
 * Constructor(boolean, String) to Constructor(String).
 * 
 * Currently configured for Hamcrest StringContains and StringStartsWith classes
 * when moving from Hamcrest 2.2+ to Hamcrest 1.x.
 * 
 * Pattern: Removes the first boolean parameter from 2-argument constructors
 * of specific classes.
 * 
 * Usage: java -cp ... github.chains.Main <sourceDir> <outputDir>
 */
public class Main {
    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: java -jar spoon-transform.jar <sourceDir> <outputDir>");
            System.err.println("Transforms code to fix breaking dependency updates");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        String outputDir = args[1];
        
        System.out.println("Transforming " + sourceDir + " to " + outputDir);
        System.out.println("Fixing Hamcrest API change: StringContains(boolean,String) -> StringContains(String)");
        System.out.println("Fixing Hamcrest API change: StringStartsWith(boolean,String) -> StringStartsWith(String)");
        
        // Create Spoon launcher
        spoon.Launcher launcher = new spoon.Launcher();
        launcher.getEnvironment().setAutoImports(true);
        launcher.addInputResource(sourceDir);
        launcher.setSourceOutputDirectory(outputDir);
        
        // Add processor for StringContains constructor calls
        launcher.addProcessor(new AbstractProcessor<CtConstructorCall<?>>() {
            @Override
            public void process(CtConstructorCall<?> constructorCall) {
                // Check if this is a StringContains constructor call
                if (constructorCall.getType() != null && 
                    constructorCall.getType().getQualifiedName() != null &&
                    constructorCall.getType().getQualifiedName().equals("org.hamcrest.core.StringContains")) {
                    
                    // Check if it has 2 arguments (boolean, String)
                    if (constructorCall.getArguments().size() == 2) {
                        System.out.println("Found StringContains(boolean, String) at: " + constructorCall.getPosition());
                        
                        // Get the string argument (second argument)
                        var stringArg = constructorCall.getArguments().get(1);
                        
                        // Create new constructor call with just the string argument
                        var factory = constructorCall.getFactory();
                        var newConstructorCall = factory.createConstructorCall(
                            factory.Type().createReference("org.hamcrest.core.StringContains"),
                            stringArg
                        );
                        
                        // Replace the old constructor call
                        constructorCall.replace(newConstructorCall);
                        
                        System.out.println("  WARNING: Boolean parameter removed. If 'true' meant 'ignoreCase',");
                        System.out.println("  behavior may change. Consider manual review.");
                    }
                }
                
                // Check for StringStartsWith constructor calls
                if (constructorCall.getType() != null && 
                    constructorCall.getType().getQualifiedName() != null &&
                    constructorCall.getType().getQualifiedName().equals("org.hamcrest.core.StringStartsWith")) {
                    
                    // Check if it has 2 arguments (boolean, String)
                    if (constructorCall.getArguments().size() == 2) {
                        System.out.println("Found StringStartsWith(boolean, String) at: " + constructorCall.getPosition());
                        
                        // Get the string argument (second argument)
                        var stringArg = constructorCall.getArguments().get(1);
                        
                        // Create new constructor call with just the string argument
                        var factory = constructorCall.getFactory();
                        var newConstructorCall = factory.createConstructorCall(
                            factory.Type().createReference("org.hamcrest.core.StringStartsWith"),
                            stringArg
                        );
                        
                        // Replace the old constructor call
                        constructorCall.replace(newConstructorCall);
                        
                        System.out.println("  WARNING: Boolean parameter removed. If 'true' meant 'ignoreCase',");
                        System.out.println("  behavior may change. Consider manual review.");
                    }
                }
            }
            
            @Override
            public boolean isToBeProcessed(CtConstructorCall<?> element) {
                // Process all constructor calls
                return true;
            }
        });
        
        // Run the transformation
        launcher.run();
        
        System.out.println("Transformation complete!");
        System.out.println("NOTE: This transformation handles the structural change but may alter");
        System.out.println("behavior if the boolean parameter controlled case sensitivity.");
        System.out.println("For projects affected by Hamcrest 2.2 -> 1.x downgrade due to");
        System.out.println("asto-core v1.15.4 transitive dependency changes.");
    }
}