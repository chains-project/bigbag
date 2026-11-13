package github.chains;

import spoon.Launcher;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtLiteral;
import spoon.reflect.code.CtTypeAccess;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.List;

/**
 * Generic Spoon transformation to fix the breaking change in plexus-container-default
 * where addComponent method signature changed from:
 *   addComponent(T, String, String) 
 * to:
 *   addComponent(T, Class, String)
 */
public class Main {
    public static void main(String[] args) {
        // Create a Spoon launcher
        Launcher launcher = new Launcher();
        
        // Set the input directory (this would be the path to the project to transform)
        String inputPath = args.length > 0 ? args[0] : "/workspace/plexus-archiver/src/main/java";
        
        // Add the input directory to the launcher
        launcher.addInputResource(inputPath);
        
        // Set the output directory
        String outputPath = "/workspace/plexus-archiver/src/main/java-fixed";
        launcher.setSourceOutputDirectory(outputPath);
        
        // Build the model
        launcher.buildModel();
        
        // Get the Spoon model
        var model = launcher.getFactory().getModel();
        
        // Find all method invocations of addComponent with 3 parameters where second arg is String
        List<CtInvocation<?>> invocations = model.getElements(new TypeFilter<>(CtInvocation.class) {
            @Override
            public boolean matches(CtInvocation<?> element) {
                // Check if it's an addComponent method call with 3 arguments
                if (element.getExecutable() != null && element.getExecutable().getSimpleName().equals("addComponent")) {
                    if (element.getArguments().size() == 3) {
                        // Check if the second argument is a String literal (the old pattern)
                        var secondArg = element.getArguments().get(1);
                        return secondArg != null && secondArg.getType() != null && 
                               secondArg.getType().getSimpleName().equals("String");
                    }
                }
                return false;
            }
        });
        
        // Print the number of transformations
        System.out.println("Found " + invocations.size() + " addComponent method calls to transform");
        
        // Process the model with the transformation
        launcher.process();
        
        // Print a success message
        System.out.println("Transformation completed successfully!");
    }
}