package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.List;

/**
 * Generic Spoon transformation to fix Hamcrest StringContains constructor calls.
 * Changes: new StringContains(true, "some_string") -> new StringContains("some_string")
 */
public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source_directory>");
            System.exit(1);
        }

        String sourceDirectory = args[0];

        Launcher launcher = new Launcher();
        launcher.addInputResource(sourceDirectory);
        launcher.setSourceOutputDirectory(sourceDirectory + "_fixed");
        
        // Set up the configuration
        launcher.getEnvironment().setComplianceLevel(8);
        launcher.getEnvironment().setAutoImports(true);
        
        // Build the model
        CtModel model = launcher.buildModel();
        
        // Find all constructor calls
        List<CtConstructorCall<?>> calls = model.getElements(new TypeFilter<>(CtConstructorCall.class));

        int modifiedCount = 0;
        for (CtConstructorCall<?> call : calls) {
            // Check if it's a StringContains constructor call by checking the qualified name
            if (call.getType().getQualifiedName().endsWith(".StringContains")) {
                // Check if it has 2 arguments with the first being a boolean literal true
                if (call.getArguments().size() == 2) {
                    CtElement firstArg = call.getArguments().get(0);
                    CtElement secondArg = call.getArguments().get(1);
                    
                    // Check if first argument is boolean true literal
                    if (firstArg instanceof spoon.reflect.code.CtLiteral && 
                        ((spoon.reflect.code.CtLiteral) firstArg).getValue().equals(true)) {
                        
                        // Remove the first argument (the boolean true)
                        call.getArguments().remove(0);
                        modifiedCount++;
                    }
                }
            }
            
            // Also check for StringStartsWith constructor calls which also had the same issue
            if (call.getType().getQualifiedName().endsWith(".StringStartsWith")) {
                // Check if it has 2 arguments with the first being a boolean literal true
                if (call.getArguments().size() == 2) {
                    CtElement firstArg = call.getArguments().get(0);
                    CtElement secondArg = call.getArguments().get(1);
                    
                    // Check if first argument is boolean true literal
                    if (firstArg instanceof spoon.reflect.code.CtLiteral && 
                        ((spoon.reflect.code.CtLiteral) firstArg).getValue().equals(true)) {
                        
                        // Remove the first argument (the boolean true)
                        call.getArguments().remove(0);
                        modifiedCount++;
                    }
                }
            }
        }
        
        System.out.println("Modified " + modifiedCount + " StringContains/StringStartsWith constructor calls");
        
        // Process and write the modified code to the output directory
        launcher.process();
        
        System.out.println("Transformation complete. Fixed files written to " + sourceDirectory + "_fixed");
    }
}