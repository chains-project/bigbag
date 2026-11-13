package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.List;

/**
 * Generic Spoon transformation to fix Hamcrest StringContains constructor calls
 * that were changed in newer versions of Hamcrest library.
 */
public class Main {
    public static void main(String[] args) {
        // Parse the source code
        Launcher launcher = new Launcher();
        launcher.addInputResource("/workspace/docker-adapter/src/main/java");
        launcher.setSourceOutputDirectory("/workspace/docker-adapter/src/main/java");
        CtModel model = launcher.buildModel();
        
        // Find all StringContains constructor calls with boolean as first argument
        List<CtConstructorCall<?>> constructorCalls = model.getElements(new TypeFilter<>(CtConstructorCall.class) {
            @Override
            public boolean matches(CtConstructorCall<?> element) {
                // Check if the constructor is for StringContains
                if (element.getType().getSimpleName().equals("StringContains")) {
                    // Check if it has exactly 2 arguments and first is boolean
                    return element.getArguments().size() == 2 &&
                           element.getArguments().get(0).getType().getSimpleName().equals("boolean");
                }
                return false;
            }
        });
        
        // Fix each constructor call by removing the boolean argument
        for (CtConstructorCall<?> call : constructorCalls) {
            // Remove the first argument (boolean) and keep the second (String)
            call.getArguments().remove(0);
        }
        
        // Process the model to save changes
        launcher.process();
        
        System.out.println("Transformation completed. Fixed " + constructorCalls.size() + " StringContains constructor calls.");
    }
}