package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.reflect.reference.CtExecutableReference;

import java.util.List;

/**
 * Generic Spoon transformation to fix Hamcrest API changes.
 * Specifically addresses StringContains constructor signature changes.
 * This transformation replaces calls to StringContains(boolean, String) with StringContains(String).
 */
public class Main {
    public static void main(String[] args) {
        // Get the input directory from command line arguments
        String inputPath = args.length > 0 ? args[0] : "/workspace/docker-adapter/src";
        
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setComplianceLevel(8);
        launcher.addInputResource(inputPath);
        launcher.buildModel();
        
        CtModel model = launcher.getModel();
        
        // Find all StringContains constructor calls with wrong signature (boolean, String)
        List<CtConstructorCall<?>> constructorCalls = model.getElements(new TypeFilter<>(CtConstructorCall.class) {
            @Override
            public boolean matches(CtConstructorCall<?> element) {
                // Check if it's a StringContains constructor call
                if (element.getExecutable() != null && 
                    element.getExecutable().getDeclaringType() != null &&
                    element.getExecutable().getDeclaringType().getSimpleName().equals("StringContains")) {
                    
                    // Check if it has 2 arguments: boolean and String
                    return element.getArguments().size() == 2;
                }
                return false;
            }
        });
        
        // Fix each constructor call by removing the first argument
        for (CtConstructorCall<?> call : constructorCalls) {
            if (call.getArguments().size() >= 2) {
                // Remove the first argument (boolean) and keep the second (String)
                call.getArguments().remove(0);
            }
        }
        
        // Print info about what was fixed
        System.out.println("Fixed " + constructorCalls.size() + " StringContains constructor calls");
        
        // Output the modified model
        launcher.setSourceOutputDirectory("/tmp/fixed");
        launcher.process();
    }
}