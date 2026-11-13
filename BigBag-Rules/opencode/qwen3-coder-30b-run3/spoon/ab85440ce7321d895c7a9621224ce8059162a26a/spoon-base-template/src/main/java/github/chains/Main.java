package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.List;

/**
 * Generic Spoon transformation to fix Hamcrest constructor signature changes.
 * Specifically fixes cases where StringContains/StartsWith constructors were changed from (String) to (boolean, String).
 * This removes the erroneous boolean parameter that was added.
 */
public class Main {
    public static void main(String[] args) {
        System.out.println("Starting Hamcrest constructor fix transformation...");
        
        // Parse the source code
        Launcher launcher = new Launcher();
        launcher.addInputResource(args.length > 0 ? args[0] : "/workspace/docker-adapter");
        launcher.setSourceOutputDirectory("/tmp/spoon-output");
        
        // Process the code
        CtModel model = launcher.buildModel();
        
        int stringContainsFixed = 0;
        int stringStartsWithFixed = 0;
        
        // Find and fix StringContains constructor calls with wrong signature (boolean, String)
        List<CtConstructorCall<?>> stringContainsCalls = model.getElements(new TypeFilter<>(CtConstructorCall.class) {
            @Override
            public boolean matches(CtConstructorCall<?> element) {
                // Match StringContains constructor calls
                if (element.getType().getQualifiedName().equals("org.hamcrest.core.StringContains")) {
                    // Check if it has the wrong signature (boolean, String) instead of just String
                    if (element.getArguments().size() == 2) {
                        // The first argument should be boolean, second should be String
                        return element.getArguments().get(0).getType().getQualifiedName().equals("boolean") &&
                               element.getArguments().get(1).getType().getQualifiedName().equals("java.lang.String");
                    }
                }
                return false;
            }
        });
        
        // Fix each StringContains constructor call by removing the first (boolean) argument
        for (CtConstructorCall<?> constructor : stringContainsCalls) {
            constructor.getArguments().remove(0);
            stringContainsFixed++;
        }

        // Find and fix StringStartsWith constructor calls with wrong signature (boolean, String)
        List<CtConstructorCall<?>> stringStartsWithCalls = model.getElements(new TypeFilter<>(CtConstructorCall.class) {
            @Override
            public boolean matches(CtConstructorCall<?> element) {
                // Match StringStartsWith constructor calls
                if (element.getType().getQualifiedName().equals("org.hamcrest.core.StringStartsWith")) {
                    // Check if it has the wrong signature (boolean, String) instead of just String
                    if (element.getArguments().size() == 2) {
                        // The first argument should be boolean, second should be String
                        return element.getArguments().get(0).getType().getQualifiedName().equals("boolean") &&
                               element.getArguments().get(1).getType().getQualifiedName().equals("java.lang.String");
                    }
                }
                return false;
            }
        });

        // Fix each StringStartsWith constructor call by removing the first (boolean) argument
        for (CtConstructorCall<?> constructor : stringStartsWithCalls) {
            constructor.getArguments().remove(0);
            stringStartsWithFixed++;
        }

        // Print the results
        System.out.println("Transformation completed.");
        System.out.println("Fixed " + stringContainsFixed + " StringContains constructor calls.");
        System.out.println("Fixed " + stringStartsWithFixed + " StringStartsWith constructor calls.");
        
        // Process and write output
        launcher.process();
        
        System.out.println("Transformation applied to source files.");
    }
}