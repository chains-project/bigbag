package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtExpression;

import java.util.List;

/**
 * Generic Spoon transformation tool to fix breaking changes in asto-core dependency.
 * This transformation addresses the breaking change where com.artipie.asto.factory.Storages
 * was replaced with com.artipie.asto.factory.StoragesLoader.
 */
public class Main {
    public static void main(String[] args) {
        // Process command-line arguments
        if (args.length < 1) {
            System.err.println("Usage: java Main <source_directory>");
            System.exit(1);
        }
        
        String sourceDirectory = args[0];
        
        // Create Spoon launcher
        Launcher launcher = new Launcher();
        launcher.addInputResource(sourceDirectory);
        
        // Process the model
        CtModel model = launcher.buildModel();
        
        // Find all constructor invocations of Storages class
        List<CtConstructorCall<?>> storagesCalls = model.getElements(new TypeFilter<>(CtConstructorCall.class) {
            @Override
            public boolean matches(CtConstructorCall<?> element) {
                return "com.artipie.asto.factory.Storages".equals(element.getType().getQualifiedName());
            }
        });
        
        // Replace all Storages constructor calls with StoragesLoader
        for (CtConstructorCall<?> call : storagesCalls) {
            // Check if we're dealing with a constructor call to Storages
            if (call.getType() != null && "com.artipie.asto.factory.Storages".equals(call.getType().getQualifiedName())) {
                // Create new constructor call to StoragesLoader instead
                CtConstructorCall<?> newCall = call.getFactory().Code().createConstructorCall(
                    call.getFactory().Type().createReference("com.artipie.asto.factory.StoragesLoader")
                );
                
                // Replace the old call with the new one
                call.replace(newCall);
            }
        }
        
        // Print results
        System.out.println("Transformed " + storagesCalls.size() + " Storages constructor calls");
        
        System.out.println("Transformation completed successfully");
    }
}